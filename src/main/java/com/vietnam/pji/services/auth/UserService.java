package com.vietnam.pji.services.auth;

import com.vietnam.pji.dto.request.ChangePasswordRequestDTO;
import com.vietnam.pji.dto.request.UpdateOwnProfileRequestDTO;
import com.vietnam.pji.dto.request.UserRequestDTO;
import com.vietnam.pji.dto.response.PaginationResultDTO;
import com.vietnam.pji.dto.response.UserDetailResponse;
import com.vietnam.pji.model.auth.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

public interface UserService {

    UserDetailResponse create(UserRequestDTO data);

    UserDetailResponse update(UserRequestDTO data);

    UserDetailResponse getInfo(Long id);

    PaginationResultDTO getAll(Specification<User> spec, Pageable pageable);

    void delete(Long id);

    // Auth helpers
    User handleGetUserByUsername(String username);

    /**
     * Look a user up by persisted id.
     *
     * <p>
     * Needed where a principal has already been resolved and validated and the
     * caller only needs the entity for a relation (for example stamping the author
     * of a clinical decision), so it does not have to re-derive an email and pay a
     * second lookup.
     */
    User handleGetUserById(Long id);

    User fetchWithTokenAndEmail(String token, String email);

    void saveRefreshToken(String token, String email);

    void updateLastLogin(String email);

    /**
     * Apply a self-service profile update for the user identified by email.
     * Only the fields named on {@link UpdateOwnProfileRequestDTO} are touched
     * — role, status, email, and password are not editable here.
     */
    User updateOwnProfile(String email, UpdateOwnProfileRequestDTO data);

    /**
     * Self-service password change. Verifies the current password against the
     * stored hash, rejects re-use of the current password, then revokes the
     * user's refresh token (DB + Redis) so all sessions must re-authenticate.
     */
    void changeOwnPassword(String email, ChangePasswordRequestDTO data);
}
