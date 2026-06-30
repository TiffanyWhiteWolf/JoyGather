-- A friend conversation belongs to both users. V4 only enrolled the demo user,
-- which meant the other friend could neither see nor send messages in it.
INSERT IGNORE INTO conversation_participants (conversation_id, user_id)
SELECT id, friend_user_id
FROM conversations
WHERE friend_user_id IS NOT NULL;
