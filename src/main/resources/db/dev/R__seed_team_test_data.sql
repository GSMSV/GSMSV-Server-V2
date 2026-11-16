INSERT INTO users (id, datagsm_id, email, name, role, status, grade, class_number, student_number, created_at)
VALUES ('user-a1b2c3d4e5f6', 'local-student-1', 'local-student-1@example.invalid', '로컬 팀장', 'USER', 'ACTIVE', 2, 1, 1, now()),
       ('user-b2c3d4e5f6g7', 'local-student-2', 'local-student-2@example.invalid', '로컬 팀원', 'USER', 'ACTIVE', 2, 1, 2, now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO teams (id, name, status, rejection_reason, created_at, updated_at)
VALUES ('team-c3d4e5f6g7h8', '로컬 테스트 팀', 'APPROVED', NULL, now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO team_members (id, team_id, user_id, role)
VALUES ('mbr-2x7c5v9bq4ta', 'team-c3d4e5f6g7h8', 'user-a1b2c3d4e5f6', 'LEADER'),
       ('mbr-9k3m7q2p5wzh', 'team-c3d4e5f6g7h8', 'user-b2c3d4e5f6g7', 'MEMBER')
ON CONFLICT (team_id, user_id) DO NOTHING;
