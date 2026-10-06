ALTER TABLE test
    ADD COLUMN name INT NOT NULL DEFAULT 1;

COPY test (id, title, name) FROM STDIN;
1	first	10
2	second	20
\.
