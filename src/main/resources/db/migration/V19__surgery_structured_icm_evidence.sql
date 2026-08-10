ALTER TABLE public.surgeries
    ADD COLUMN positive_histology boolean,
    ADD COLUMN intraoperative_purulence boolean;

COMMENT ON COLUMN public.surgeries.positive_histology
    IS 'Nullable structured ICM evidence: true positive, false negative, null not assessed.';

COMMENT ON COLUMN public.surgeries.intraoperative_purulence
    IS 'Nullable structured ICM evidence: true purulence present, false absent, null not assessed.';
