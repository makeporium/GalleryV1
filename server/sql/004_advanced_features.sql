-- 11. Views
-- 7. Joins
-- 8. Group By & Having
-- 9. Subqueries
CREATE OR REPLACE VIEW feed_view AS
SELECT 
    p.id AS post_id,
    p.caption,
    p.status,
    p.created_at AS post_created_at,
    u.id AS user_id,
    u.name AS user_name,
    u.avatar_url,
    (SELECT media_url FROM post_media pm WHERE pm.post_id = p.id ORDER BY id ASC LIMIT 1) AS media_url,
    COUNT(DISTINCT pl.user_id) AS likes_count,
    COUNT(DISTINCT pc.id) AS comments_count
FROM posts p
JOIN users u ON p.user_id = u.id
LEFT JOIN post_likes pl ON p.id = pl.post_id
LEFT JOIN post_comments pc ON p.id = pc.post_id
GROUP BY p.id, p.caption, p.status, p.created_at, u.id, u.name, u.avatar_url
HAVING likes_count >= 0;

-- 12. Stored Procedures
-- 13. Transactions (ACID, COMMIT, ROLLBACK)
DROP PROCEDURE IF EXISTS create_post_with_media;
CREATE PROCEDURE create_post_with_media(
    IN p_user_id BIGINT UNSIGNED,
    IN p_prompt_id BIGINT UNSIGNED,
    IN p_caption TEXT,
    IN p_media_url TEXT,
    OUT p_post_id BIGINT UNSIGNED
)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    INSERT INTO posts (user_id, prompt_id, caption, status, created_at, updated_at, submitted_at)
    VALUES (p_user_id, p_prompt_id, p_caption, 'published', NOW(), NOW(), NOW());
    
    SET p_post_id = LAST_INSERT_ID();

    INSERT INTO post_media (post_id, media_url, created_at, updated_at)
    VALUES (p_post_id, p_media_url, NOW(), NOW());

    COMMIT;
END;

