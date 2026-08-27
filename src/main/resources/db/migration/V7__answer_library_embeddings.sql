-- Semantic search over the answer library.
--
-- The library is what makes the product improve with use: once a question has
-- been answered and approved, the next questionnaire asking the same thing in
-- different words should reuse it. That needs an embedding, which the original
-- schema had no column for -- so "semantic" matching fell back to Jaccard word
-- overlap at a 0.85 threshold, which almost never fires across rewordings.

ALTER TABLE vanta_answer_library
    ADD COLUMN IF NOT EXISTS embedding vector(1536);

-- Dimension of the model that produced each embedding. Stored because changing
-- embedding models invalidates every stored vector, and a silent mismatch
-- produces confident nonsense rather than an error.
ALTER TABLE vanta_answer_library
    ADD COLUMN IF NOT EXISTS embedding_model VARCHAR(128);

ALTER TABLE vanta_answer_library
    ADD COLUMN IF NOT EXISTS embedded_at TIMESTAMPTZ;

-- IVFFlat over cosine distance. `lists` is deliberately small: these libraries
-- are thousands of rows, not millions, and an oversized list count degrades
-- recall on small tables.
CREATE INDEX IF NOT EXISTS idx_answer_library_embedding
    ON vanta_answer_library
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 50);

-- Semantic lookup always filters to live entries of one tenant before ranking.
CREATE INDEX IF NOT EXISTS idx_answer_library_semantic_scope
    ON vanta_answer_library (tenant_id, question_type)
    WHERE deleted_at IS NULL AND embedding IS NOT NULL;
