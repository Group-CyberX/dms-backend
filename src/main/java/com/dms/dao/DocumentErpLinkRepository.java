package com.dms.dao;

import com.dms.models.DocumentErpLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentErpLinkRepository extends JpaRepository<DocumentErpLink, UUID> {
    List<DocumentErpLink> findByDocumentId(UUID documentId);
    Optional<DocumentErpLink> findFirstByDocumentId(UUID documentId);
    Optional<DocumentErpLink> findByDocumentIdAndTransactionId(UUID documentId, UUID transactionId);
    boolean existsByDocumentIdAndTransactionId(UUID documentId, UUID transactionId);
    List<DocumentErpLink> findByTransactionId(UUID transactionId);

    /**
     * Deletes every link riding on one connection's transactions, as a single
     * statement with a subquery rather than fetching the transaction ids into
     * Java first and binding them back in as a collection parameter - which
     * hit a Spring Data type-conversion error passing a derived query's
     * List&lt;UUID&gt; into a second query's IN clause.
     */
    @Modifying
    @Query("delete from DocumentErpLink l where l.transactionId in "
            + "(select t.transactionId from ErpTransaction t where t.erpConnectionId = :connectionId)")
    void deleteByConnectionId(@Param("connectionId") UUID connectionId);
}
