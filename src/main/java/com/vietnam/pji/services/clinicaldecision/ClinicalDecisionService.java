package com.vietnam.pji.services.clinicaldecision;

import com.vietnam.pji.dto.request.ClinicalDecisionRevisionRequestDTO;
import com.vietnam.pji.dto.request.DoctorClinicalDecisionRequestDTO;
import com.vietnam.pji.dto.request.PharmacistClinicalDecisionRequestDTO;
import com.vietnam.pji.dto.response.ClinicalDecisionWorkspaceDTO;
import com.vietnam.pji.dto.response.ClinicalDecisionWorkspaceDTO.RunDecision;

public interface ClinicalDecisionService {
    ClinicalDecisionWorkspaceDTO getWorkspace(Long episodeId);

    RunDecision getRunDecision(Long runId);

    RunDecision saveDoctorDecision(Long runId, DoctorClinicalDecisionRequestDTO request);

    RunDecision signDoctorDecision(Long runId, ClinicalDecisionRevisionRequestDTO request);

    RunDecision savePharmacistDecision(Long runId, PharmacistClinicalDecisionRequestDTO request);

    RunDecision signPharmacistDecision(Long runId, ClinicalDecisionRevisionRequestDTO request);

    RunDecision selectFinalRun(Long episodeId, Long runId);
}
