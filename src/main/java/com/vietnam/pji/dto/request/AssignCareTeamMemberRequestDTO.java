package com.vietnam.pji.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Request to grant a user access to an episode's clinical record. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignCareTeamMemberRequestDTO {

    @NotNull(message = "must not be null")
    private Long userId;

    /**
     * OWNER, DOCTOR, PHARMACIST or NURSE. Granted members may read the episode;
     * only OWNER and ADMIN may change the team.
     */
    @NotBlank(message = "must not be blank")
    private String memberRole;
}
