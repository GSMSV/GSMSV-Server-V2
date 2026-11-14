-- 접속 비밀번호는 다시 보여 줘야 해서 해시가 아니라 암호화한 값을 둔다 (24번 DBaaS 6장).
-- 공유 DB 서버에 계정을 만들기 전(creating)에는 값이 없으므로 NULL을 허용한다
ALTER TABLE databases
    ADD COLUMN password_encrypted TEXT;
