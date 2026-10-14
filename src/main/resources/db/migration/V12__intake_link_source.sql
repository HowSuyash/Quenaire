-- Questionnaires that arrive through an intake link get their own source, so a
-- customer can tell them apart from the ones their own staff uploaded.
--
-- On its own because ADD VALUE is one of the statements Flyway may run outside
-- a transaction, and it refuses a migration that mixes the two kinds.
ALTER TYPE vanta_questionnaire_source ADD VALUE IF NOT EXISTS 'INTAKE_LINK';
