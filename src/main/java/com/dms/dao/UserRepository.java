package com.dms.dao;

import com.dms.models.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    Optional<User> findByResetToken(String token);

    /**
     * One page of the user directory, searched and filtered in the database.
     *
     * The management screen used to load every user and then search the array
     * in the browser, which meant the whole table crossed the network before a
     * single row was hidden.
     */
    @Query("""
            select u from User u
            where (:search is null or :search = ''
                   or lower(u.username) like lower(concat('%', :search, '%'))
                   or lower(u.email) like lower(concat('%', :search, '%')))
              and (:status is null or :status = '' or upper(u.status) = upper(:status))
              and (:roleName is null or :roleName = '' or upper(u.role.name) = upper(:roleName))
            """)
    Page<User> search(@Param("search") String search,
                      @Param("status") String status,
                      @Param("roleName") String roleName,
                      Pageable pageable);

    /**
     * Header counts for the management screen.
     *
     * These are totals across the whole directory, so they cannot be derived
     * from the page on screen - the page holds ten rows and the answer is about
     * all of them.
     */
    long countByStatusIgnoreCase(String status);

    @Query("select count(distinct u.role.name) from User u")
    long countDistinctRoles();
}