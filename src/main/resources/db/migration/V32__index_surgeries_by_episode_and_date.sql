-- Supports SurgeryRepository.findByEpisodeIdOrderBySurgeryDateAsc and episode cascade deletes.
CREATE INDEX idx_surgeries_episode_surgery_date
    ON public.surgeries (episode_id, surgery_date ASC);
