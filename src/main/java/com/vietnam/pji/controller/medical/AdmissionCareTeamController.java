package com.vietnam.pji.controller.medical;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vietnam.pji.constant.CareTeamMemberRole;
import com.vietnam.pji.dto.request.AssignCareTeamMemberRequestDTO;
import com.vietnam.pji.dto.response.AdmissionCareTeamMemberDTO;
import com.vietnam.pji.dto.response.ResponseData;
import com.vietnam.pji.exception.InvalidDataException;
import com.vietnam.pji.services.episode.AdmissionCareTeamService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Care-team membership of an episode: the API that grants and revokes clinical
 * record access.
 *
 * <p>
 * This is the counterpart to the deny-by-default decision in
 * {@code docs/decisions/0008-general-authorization-policy.md}. Access to a
 * clinical record is ownership or care-team membership; without an endpoint to
 * create membership, the only way to let a colleague - notably a pharmacist, who
 * never creates an episode - reach a record would be a blanket role-wide
 * exemption, which is exactly the privilege bypass this replaces.
 *
 * <p>
 * The controller is thin: no membership rule is decided here.
 */
@RestController
@RequestMapping("${api.prefix}")
@Validated
@Tag(name = "Episode Care Team", description = "Grant, list and revoke clinical record access for an episode")
@RequiredArgsConstructor
public class AdmissionCareTeamController {

    private final AdmissionCareTeamService careTeamService;

    @Operation(summary = "List the episode's care team",
            description = "Requires ownership, care-team membership or an active administrator.")
    @GetMapping({"/admissions/{episodeId}/care-team", "/episodes/{episodeId}/care-team"})
    public ResponseData<List<AdmissionCareTeamMemberDTO>> list(@PathVariable Long episodeId) {
        return new ResponseData<>(HttpStatus.OK.value(), "Care team loaded",
                careTeamService.listMembers(episodeId));
    }

    @Operation(summary = "Grant clinical record access",
            description = "Requires the caller to be the episode's owner or an active administrator. "
                    + "Re-activates a previously revoked membership. Role must be "
                    + "OWNER, DOCTOR, PHARMACIST or NURSE.")
    @PostMapping({"/admissions/{episodeId}/care-team", "/episodes/{episodeId}/care-team"})
    public ResponseData<AdmissionCareTeamMemberDTO> assign(
            @PathVariable Long episodeId,
            @Valid @RequestBody AssignCareTeamMemberRequestDTO request) {

        CareTeamMemberRole memberRole = CareTeamMemberRole.from(request.getMemberRole());
        if (memberRole == null) {
            throw new InvalidDataException(
                    "Unknown care-team role. Expected OWNER, DOCTOR, PHARMACIST or NURSE");
        }
        return new ResponseData<>(HttpStatus.OK.value(), "Care team member assigned",
                careTeamService.assignMember(episodeId, request.getUserId(), memberRole));
    }

    @Operation(summary = "Revoke clinical record access",
            description = "Requires the caller to be the episode's owner or an active administrator. "
                    + "The last active owner cannot be revoked.")
    @DeleteMapping({"/admissions/{episodeId}/care-team/{userId}", "/episodes/{episodeId}/care-team/{userId}"})
    public ResponseData<Void> revoke(@PathVariable Long episodeId, @PathVariable Long userId) {
        careTeamService.revokeMember(episodeId, userId);
        return new ResponseData<>(HttpStatus.OK.value(), "Care team member revoked");
    }
}
