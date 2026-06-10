-- 에이전트 메시지 영속화: type에 agent 추가, 표시용 닉네임 컬럼 추가
ALTER TABLE messages DROP CONSTRAINT chk_messages_type;
ALTER TABLE messages ADD CONSTRAINT chk_messages_type CHECK (type IN ('chat', 'system', 'agent'));
ALTER TABLE messages ADD COLUMN agent_nickname VARCHAR(50);
