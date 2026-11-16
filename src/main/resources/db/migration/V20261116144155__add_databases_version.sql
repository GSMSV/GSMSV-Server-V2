-- 로더가 돌려준 분리된 엔티티를 저장할 때 동시 요청의 변경을 덮어쓰지 않도록 낙관적 잠금 칸을 둔다
ALTER TABLE databases
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
