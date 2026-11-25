-- 口令所有 App 通用：MySQL 数据库调整
-- 顺序：停服务 → 备份数据库 → 执行本文件 → 换新版本启动。每一步都先查再改，重复执行没关系。

-- 1. 激活记录：去掉按（设备，口令）的唯一索引，后面改成按（App，设备，口令）
SET @s = (SELECT CONCAT('ALTER TABLE activation_record DROP INDEX `', index_name, '`')
          FROM information_schema.statistics
          WHERE table_schema = DATABASE() AND table_name = 'activation_record' AND non_unique = 0 AND index_name <> 'PRIMARY'
          GROUP BY index_name
          HAVING COUNT(*) = 2 AND SUM(column_name = 'device_id') = 1 AND SUM(column_name = 'code_id') = 1
          LIMIT 1);
SET @s = IFNULL(@s, 'DO 0');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 2. 同名的口令只留最早建的那个：激活记录挪过去，用过的次数加在一起
DROP TEMPORARY TABLE IF EXISTS code_keep;
CREATE TEMPORARY TABLE code_keep AS
    SELECT code, MIN(id) AS keep_id, SUM(COALESCE(used_count, 0)) AS used FROM activation_code GROUP BY code HAVING COUNT(*) > 1;
UPDATE activation_record r JOIN activation_code c ON c.id = r.code_id JOIN code_keep k ON k.code = c.code
    SET r.code_id = k.keep_id WHERE c.id <> k.keep_id;
UPDATE activation_code c JOIN code_keep k ON k.keep_id = c.id SET c.used_count = k.used;
DELETE c FROM activation_code c JOIN code_keep k ON k.code = c.code WHERE c.id <> k.keep_id;
DROP TEMPORARY TABLE code_keep;

-- 3. 口令表：去掉带 app_id 的索引和 app_id 列
SET @s = (SELECT CONCAT('ALTER TABLE activation_code ', GROUP_CONCAT(DISTINCT CONCAT('DROP INDEX `', index_name, '`') SEPARATOR ', '))
          FROM information_schema.statistics
          WHERE table_schema = DATABASE() AND table_name = 'activation_code' AND column_name = 'app_id' AND index_name <> 'PRIMARY');
SET @s = IFNULL(@s, 'DO 0');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
SET @s = IF((SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'activation_code' AND column_name = 'app_id') > 0,
            'ALTER TABLE activation_code DROP COLUMN app_id', 'DO 0');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 4. 代理申请、申请里的口令、公告：去掉 app_id
SET @s = (SELECT CONCAT('ALTER TABLE agent_code_request_item ', GROUP_CONCAT(DISTINCT CONCAT('DROP INDEX `', index_name, '`') SEPARATOR ', '))
          FROM information_schema.statistics
          WHERE table_schema = DATABASE() AND table_name = 'agent_code_request_item' AND column_name = 'app_id' AND index_name <> 'PRIMARY');
SET @s = IFNULL(@s, 'DO 0');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
SET @s = IF((SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'agent_code_request_item' AND column_name = 'app_id') > 0,
            'ALTER TABLE agent_code_request_item DROP COLUMN app_id', 'DO 0');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
SET @s = IF((SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'agent_code_request' AND column_name = 'app_id') > 0,
            'ALTER TABLE agent_code_request DROP COLUMN app_id', 'DO 0');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
SET @s = IF((SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'agent_notice' AND column_name = 'app_id') > 0,
            'ALTER TABLE agent_notice DROP COLUMN app_id', 'DO 0');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 5. 代理能用的 App：整张表不要了
DROP TABLE IF EXISTS agent_app;

-- 6. 口令权限不分 App：管理员上加 manage_codes；原来在哪个 App 上勾过口令的成员，改成能管理口令
SET @s = IF((SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'ota_admin_user' AND column_name = 'manage_codes') = 0,
            'ALTER TABLE ota_admin_user ADD COLUMN manage_codes BIT(1) NOT NULL DEFAULT b''0''', 'DO 0');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
UPDATE ota_admin_user SET manage_codes = 0 WHERE manage_codes IS NULL;
ALTER TABLE ota_admin_user MODIFY manage_codes BIT(1) NOT NULL DEFAULT b'0';
UPDATE ota_admin_user SET manage_codes = 1
    WHERE owner = 0 AND id IN (SELECT admin_id FROM ota_admin_grant WHERE CONCAT(',', perms, ',') LIKE '%,codes,%');
UPDATE ota_admin_grant SET perms = TRIM(BOTH ',' FROM REPLACE(CONCAT(',', perms, ','), ',codes,', ','))
    WHERE CONCAT(',', perms, ',') LIKE '%,codes,%';

-- 7. 新的唯一约束：口令全局唯一，激活记录按（App，设备，口令）唯一
SET @s = IF((SELECT COUNT(*) FROM information_schema.statistics
             WHERE table_schema = DATABASE() AND table_name = 'activation_code' AND index_name = 'uk_activation_code') = 0,
            'ALTER TABLE activation_code ADD CONSTRAINT uk_activation_code UNIQUE (code)', 'DO 0');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
SET @s = IF((SELECT COUNT(*) FROM information_schema.statistics
             WHERE table_schema = DATABASE() AND table_name = 'activation_record' AND index_name = 'uk_activation_record') = 0,
            'ALTER TABLE activation_record ADD CONSTRAINT uk_activation_record UNIQUE (app_id, device_id, code_id)', 'DO 0');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
