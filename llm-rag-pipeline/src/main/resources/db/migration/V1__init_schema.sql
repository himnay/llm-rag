CREATE TABLE faqs (
    id SERIAL PRIMARY KEY,
    question TEXT NOT NULL,
    answer TEXT NOT NULL,
    department VARCHAR(50),
    visibility VARCHAR(20)
);

CREATE TABLE release_notes (
    id SERIAL PRIMARY KEY,
    version VARCHAR(20),
    summary TEXT,
    details TEXT,
    release_date DATE
);

CREATE TABLE announcements (
    id SERIAL PRIMARY KEY,
    subject TEXT NOT NULL,
    body TEXT NOT NULL,
    category VARCHAR(50),
    effective_from DATE,
    effective_to DATE,
    source_type VARCHAR(20)
);
