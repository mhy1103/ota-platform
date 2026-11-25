-- 口令所有 App 通用：内置数据库（H2）调整
-- 顺序：停服务 → 备份数据目录 → 执行本文件 → 换新版本启动。重复执行没关系。
-- 执行方法（<数据目录> 换成 --ota.data-dir，jar 换成你的文件名）：
--   unzip -o -j ota-platform.jar 'BOOT-INF/lib/h2-*.jar' -d /tmp/h2
--   java -cp /tmp/h2/h2-2.2.224.jar org.h2.tools.RunScript \
--     -url "jdbc:h2:file:<数据目录>/db/ota;MODE=MySQL;DATABASE_TO_LOWER=TRUE" -user sa -script h2.sql

-- 1. 激活记录：去掉按（设备，口令）的唯一约束，后面改成按（App，设备，口令）
EXECUTE IMMEDIATE COALESCE((
    SELECT 'ALTER TABLE activation_record DROP CONSTRAINT "' || tc.constraint_name || '"'
    FROM information_schema.table_constraints tc
    WHERE tc.table_name = 'activation_record' AND tc.constraint_type = 'UNIQUE'
      AND (SELECT COUNT(*) FROM information_schema.key_column_usage k
           WHERE k.table_name = tc.table_name AND k.constraint_name = tc.constraint_name) = 2
      AND EXISTS (SELECT 1 FROM information_schema.key_column_usage k
                  WHERE k.table_name = tc.table_name AND k.constraint_name = tc.constraint_name AND k.column_name = 'device_id')
      AND EXISTS (SELECT 1 FROM information_schema.key_column_usage k
                  WHERE k.table_name = tc.table_name AND k.constraint_name = tc.constraint_name AND k.column_name = 'code_id')
    FETCH FIRST 1 ROWS ONLY), 'SET @ignored = 1');

-- 2. 同名的口令只留最早建的那个：激活记录挪过去，用过的次数加在一起
UPDATE activation_record r
    SET code_id = (SELECT MIN(k.id) FROM activation_code k WHERE k.code = (SELECT c.code FROM activation_code c WHERE c.id = r.code_id))
    WHERE r.code_id IN (SELECT c.id FROM activation_code c WHERE c.id > (SELECT MIN(k.id) FROM activation_code k WHERE k.code = c.code));
UPDATE activation_code c
    SET used_count = (SELECT SUM(COALESCE(k.used_count, 0)) FROM activation_code k WHERE k.code = c.code)
    WHERE c.id = (SELECT MIN(k.id) FROM activation_code k WHERE k.code = c.code)
      AND (SELECT COUNT(*) FROM activation_code k WHERE k.code = c.code) > 1;
DELETE FROM activation_code c WHERE c.id > (SELECT MIN(k.id) FROM activation_code k WHERE k.code = c.code);

-- 3. 去掉带 app_id 的约束和索引（有几个删几个），再删 app_id 列
EXECUTE IMMEDIATE COALESCE((
    SELECT 'ALTER TABLE ' || tc.table_name || ' DROP CONSTRAINT "' || tc.constraint_name || '"'
    FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage k ON k.table_name = tc.table_name AND k.constraint_name = tc.constraint_name
    WHERE tc.table_name IN ('activation_code', 'agent_code_request_item') AND tc.constraint_type = 'UNIQUE' AND k.column_name = 'app_id'
    FETCH FIRST 1 ROWS ONLY), 'SET @ignored = 1');
EXECUTE IMMEDIATE COALESCE((
    SELECT 'ALTER TABLE ' || tc.table_name || ' DROP CONSTRAINT "' || tc.constraint_name || '"'
    FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage k ON k.table_name = tc.table_name AND k.constraint_name = tc.constraint_name
    WHERE tc.table_name IN ('activation_code', 'agent_code_request_item') AND tc.constraint_type = 'UNIQUE' AND k.column_name = 'app_id'
    FETCH FIRST 1 ROWS ONLY), 'SET @ignored = 1');
EXECUTE IMMEDIATE COALESCE((
    SELECT 'DROP INDEX "' || i.index_name || '"' FROM information_schema.index_columns i
    WHERE i.table_name IN ('activation_code', 'agent_code_request_item', 'agent_code_request', 'agent_notice') AND i.column_name = 'app_id'
    FETCH FIRST 1 ROWS ONLY), 'SET @ignored = 1');
EXECUTE IMMEDIATE COALESCE((
    SELECT 'DROP INDEX "' || i.index_name || '"' FROM information_schema.index_columns i
    WHERE i.table_name IN ('activation_code', 'agent_code_request_item', 'agent_code_request', 'agent_notice') AND i.column_name = 'app_id'
    FETCH FIRST 1 ROWS ONLY), 'SET @ignored = 1');
EXECUTE IMMEDIATE COALESCE((
    SELECT 'DROP INDEX "' || i.index_name || '"' FROM information_schema.index_columns i
    WHERE i.table_name IN ('activation_code', 'agent_code_request_item', 'agent_code_request', 'agent_notice') AND i.column_name = 'app_id'
    FETCH FIRST 1 ROWS ONLY), 'SET @ignored = 1');
ALTER TABLE activation_code DROP COLUMN IF EXISTS app_id;
ALTER TABLE agent_code_request_item DROP COLUMN IF EXISTS app_id;
ALTER TABLE agent_code_request DROP COLUMN IF EXISTS app_id;
ALTER TABLE agent_notice DROP COLUMN IF EXISTS app_id;

-- 4. 代理能用的 App：整张表不要了
DROP TABLE IF EXISTS agent_app;

-- 5. 口令权限不分 App：管理员上加 manage_codes；原来在哪个 App 上勾过口令的成员，改成能管理口令
ALTER TABLE ota_admin_user ADD COLUMN IF NOT EXISTS manage_codes BOOLEAN DEFAULT FALSE NOT NULL;
UPDATE ota_admin_user SET manage_codes = FALSE WHERE manage_codes IS NULL;
ALTER TABLE ota_admin_user ALTER COLUMN manage_codes SET DEFAULT FALSE;
ALTER TABLE ota_admin_user ALTER COLUMN manage_codes SET NOT NULL;
UPDATE ota_admin_user SET manage_codes = TRUE
    WHERE owner = FALSE AND id IN (SELECT admin_id FROM ota_admin_grant WHERE CONCAT(',', perms, ',') LIKE '%,codes,%');
UPDATE ota_admin_grant SET perms = TRIM(BOTH ',' FROM REPLACE(CONCAT(',', perms, ','), ',codes,', ','))
    WHERE CONCAT(',', perms, ',') LIKE '%,codes,%';

-- 6. 新的唯一约束：口令全局唯一，激活记录按（App，设备，口令）唯一
ALTER TABLE activation_code ADD CONSTRAINT IF NOT EXISTS uk_activation_code UNIQUE (code);
ALTER TABLE activation_record ADD CONSTRAINT IF NOT EXISTS uk_activation_record UNIQUE (app_id, device_id, code_id);
