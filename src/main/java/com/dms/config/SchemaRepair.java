package com.dms.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * One-off schema corrections that Hibernate's {@code ddl-auto=update} cannot
 * make for itself.
 *
 * {@code ddl-auto=update} only ever adds things. It will create a table or a
 * column that is missing, but it never drops or re-points an existing
 * constraint - so a foreign key that was created against the wrong table stays
 * wrong forever, on every developer's database at once. The repairs below are
 * therefore expressed as explicit, idempotent SQL that checks the current state
 * before touching anything.
 *
 * Each repair is safe to run repeatedly and logs only when it actually changes
 * something, so a healthy database starts up silently.
 */
@Component
public class SchemaRepair {

    private static final Logger log = LoggerFactory.getLogger(SchemaRepair.class);

    private final JdbcTemplate jdbc;

    public SchemaRepair(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void repair() {
        try {
            repointDigitalSignatureVersionFk();
            repointDocumentErpLinkFk();
            dropOrphanedDocumentVersionsTable();
            dropOrphanedErpExternalIdColumn();
            backfillDocumentStatus();
        } catch (Exception e) {
            // A failed repair must not stop the application from serving: the
            // rest of the system works, and the operator needs the log more
            // than they need a crash.
            log.error("Schema repair could not be completed: {}", e.getMessage(), e);
        }
    }

    /**
     * Points {@code digital_signatures.document_version_id} at the table that
     * actually holds versions.
     *
     * The entity writes versions to {@code "DocumentVersion"} (quoted, mixed
     * case), but an earlier revision of the schema also produced a snake_case
     * {@code document_versions} table, and the signature foreign key was
     * created against that one. Since nothing ever wrote to it, every attempt
     * to sign a document failed with a foreign key violation - the new version
     * genuinely did not exist in the table the constraint was checking.
     */
    private void repointDigitalSignatureVersionFk() {
        if (!tableExists("digital_signatures")) {
            return;
        }

        String currentTarget = jdbc.query(
                """
                select rel.relname
                from pg_constraint con
                join pg_class cl on cl.oid = con.conrelid
                join pg_class rel on rel.oid = con.confrelid
                where con.contype = 'f'
                  and cl.relname = 'digital_signatures'
                  and con.conname = 'digital_signatures_document_version_id_fkey'
                """,
                rs -> rs.next() ? rs.getString(1) : null);

        if (currentTarget == null || "DocumentVersion".equals(currentTarget)) {
            return; // absent or already correct
        }

        // Re-pointing would fail if a signature referenced a row that exists
        // only in the old table, so check before altering rather than after.
        Integer stranded = jdbc.queryForObject(
                """
                select count(*)
                from digital_signatures ds
                where ds.document_version_id is not null
                  and not exists (
                      select 1 from "DocumentVersion" dv
                      where dv.version_id = ds.document_version_id)
                """,
                Integer.class);

        if (stranded != null && stranded > 0) {
            log.warn("Cannot re-point digital_signatures foreign key: {} signature row(s) "
                    + "reference versions absent from \"DocumentVersion\". Resolve these manually.", stranded);
            return;
        }

        jdbc.execute("alter table digital_signatures "
                + "drop constraint digital_signatures_document_version_id_fkey");
        jdbc.execute("alter table digital_signatures "
                + "add constraint digital_signatures_document_version_id_fkey "
                + "foreign key (document_version_id) references \"DocumentVersion\"(version_id)");

        log.info("Schema repair: digital_signatures.document_version_id now references "
                + "\"DocumentVersion\" (was {}). Document signing will now persist.", currentTarget);
    }

    /**
     * Points {@code document_erp_links.document_id} at {@code "Document"}.
     *
     * Documents live in {@code "Document"}, but an early snake_case
     * {@code documents} table also survives with three rows of test data from
     * the first week of the project, and this foreign key was created against
     * it. Linking a document to an ERP transaction therefore failed for every
     * real document. Two other constraints point at the same stale table
     * ({@code document_tags} and {@code share_links}), but both of those tables
     * are themselves unused duplicates - tags live in {@code "DocumentTag"} and
     * share links in {@code share_link} - so they are left alone.
     */
    private void repointDocumentErpLinkFk() {
        if (!tableExists("document_erp_links")) {
            return;
        }

        String currentTarget = jdbc.query(
                """
                select rel.relname
                from pg_constraint con
                join pg_class cl on cl.oid = con.conrelid
                join pg_class rel on rel.oid = con.confrelid
                where con.contype = 'f'
                  and cl.relname = 'document_erp_links'
                  and con.conname = 'document_erp_links_document_id_fkey'
                """,
                rs -> rs.next() ? rs.getString(1) : null);

        if (currentTarget == null || "Document".equals(currentTarget)) {
            return;
        }

        Integer stranded = jdbc.queryForObject(
                """
                select count(*)
                from document_erp_links l
                where l.document_id is not null
                  and not exists (
                      select 1 from "Document" d where d.document_id = l.document_id)
                """,
                Integer.class);

        if (stranded != null && stranded > 0) {
            log.warn("Cannot re-point document_erp_links foreign key: {} link(s) reference "
                    + "documents absent from \"Document\". Resolve these manually.", stranded);
            return;
        }

        jdbc.execute("alter table document_erp_links "
                + "drop constraint document_erp_links_document_id_fkey");
        jdbc.execute("alter table document_erp_links "
                + "add constraint document_erp_links_document_id_fkey "
                + "foreign key (document_id) references \"Document\"(document_id)");

        log.info("Schema repair: document_erp_links.document_id now references \"Document\" "
                + "(was {}). Document-to-ERP linking will now work.", currentTarget);
    }

    /**
     * Removes the empty snake_case {@code document_versions} table once nothing
     * points at it, so the duplicate cannot capture a future foreign key the
     * same way it captured the signature one.
     */
    private void dropOrphanedDocumentVersionsTable() {
        if (!tableExists("document_versions")) {
            return;
        }

        Integer rows = jdbc.queryForObject("select count(*) from document_versions", Integer.class);
        if (rows == null || rows > 0) {
            log.warn("Leftover table document_versions holds {} row(s); leaving it in place.", rows);
            return;
        }

        Integer referencing = jdbc.queryForObject(
                """
                select count(*)
                from pg_constraint con
                join pg_class rel on rel.oid = con.confrelid
                where con.contype = 'f' and rel.relname = 'document_versions'
                """,
                Integer.class);

        if (referencing != null && referencing > 0) {
            log.warn("Leftover table document_versions still has {} foreign key(s) pointing at it; "
                    + "leaving it in place.", referencing);
            return;
        }

        jdbc.execute("drop table document_versions");
        log.info("Schema repair: dropped the empty duplicate table document_versions.");
    }

    /**
     * Removes {@code erp_transactions.external_erp_id}, a NOT NULL column left
     * over from an earlier schema that no entity maps.
     *
     * The synchroniser stores an ERP's reference in {@code external_ref}, which
     * {@code ddl-auto=update} duly added alongside the old column - but it
     * cannot drop the old one or relax its NOT NULL. Every inserted transaction
     * therefore violated the constraint, which poisoned the surrounding
     * transaction and surfaced as "Transaction silently rolled back", hiding the
     * real cause. No ERP sync had ever stored a row.
     */
    private void dropOrphanedErpExternalIdColumn() {
        if (!columnExists("erp_transactions", "external_erp_id")) {
            return;
        }

        Integer populated = jdbc.queryForObject(
                "select count(*) from erp_transactions where external_erp_id is not null", Integer.class);
        if (populated != null && populated > 0) {
            // Real data would need a considered migration, not a drop. Relax the
            // constraint instead so syncing works and the values are preserved.
            jdbc.execute("alter table erp_transactions alter column external_erp_id drop not null");
            log.info("Schema repair: erp_transactions.external_erp_id holds {} value(s); "
                    + "dropped its NOT NULL constraint so ERP sync can insert.", populated);
            return;
        }

        jdbc.execute("alter table erp_transactions drop column external_erp_id");
        log.info("Schema repair: dropped the unused NOT NULL column "
                + "erp_transactions.external_erp_id. ERP sync will now store transactions.");
    }

    /**
     * Gives documents that predate the status column the status they already
     * appear to have.
     *
     * Approval state used to live only on the workflow, and the document list
     * read it back from {@code workflow_instance} on every load. Now that a
     * document carries its own status, existing rows would otherwise all read as
     * NEW and a library of settled documents would resurface as unhandled work.
     * So the status is taken from each document's most recent workflow, and only
     * documents that never had one become NEW.
     *
     * Runs once: as soon as any row has a status, there is nothing to derive.
     */
    private void backfillDocumentStatus() {
        if (!columnExists("Document", "status")) {
            return;
        }

        Integer alreadySet = jdbc.queryForObject(
                "select count(*) from \"Document\" where status is not null", Integer.class);
        if (alreadySet != null && alreadySet > 0) {
            return;
        }

        int fromWorkflows = 0;
        if (tableExists("workflow_instance")) {
            fromWorkflows = jdbc.update(
                    "update \"Document\" d set status = w.status "
                            + "from (select distinct on (document_id) document_id, status "
                            + "        from workflow_instance order by document_id, id desc) w "
                            + "where d.document_id::text = w.document_id");
        }

        int asNew = jdbc.update("update \"Document\" set status = 'NEW' where status is null");

        if (fromWorkflows > 0 || asNew > 0) {
            log.info("Document status backfilled: {} from their latest workflow, {} as NEW.",
                    fromWorkflows, asNew);
        }
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbc.queryForObject(
                "select count(*) from information_schema.columns "
                        + "where table_schema = 'public' and table_name = ? and column_name = ?",
                Integer.class, table, column);
        return count != null && count > 0;
    }

    private boolean tableExists(String name) {
        Integer count = jdbc.queryForObject(
                "select count(*) from information_schema.tables "
                        + "where table_schema = 'public' and table_name = ?",
                Integer.class, name);
        return count != null && count > 0;
    }
}
