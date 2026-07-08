-- ============================================================
-- 住宅小区物业管理系统 — 数据库建表脚本
-- DBMS: MySQL 8.0+
-- ============================================================

-- 1. 创建数据库
DROP DATABASE IF EXISTS community_property;
CREATE DATABASE community_property
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE community_property;

-- ============================================================
-- 2. 建表（按依赖顺序）
-- ============================================================

-- --------------------------------------
-- 2.1 楼栋信息表
-- --------------------------------------
CREATE TABLE building (
  building_no    VARCHAR(10)    NOT NULL,
  floor_count    INT            NOT NULL,
  units_per_floor INT           NOT NULL,
  create_time    DATETIME       DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (building_no),
  CONSTRAINT chk_building_floor_count CHECK (floor_count >= 1),
  CONSTRAINT chk_building_units     CHECK (units_per_floor >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='楼栋信息表';

-- --------------------------------------
-- 2.2 住户信息表
-- --------------------------------------
CREATE TABLE household (
  household_id        INT            AUTO_INCREMENT,
  building_no         VARCHAR(10)    NOT NULL,
  floor_no            INT            NOT NULL,
  unit_no             INT            NOT NULL,
  area                DECIMAL(10,2)  NOT NULL,
  property_fee_rate   DECIMAL(10,2)  NOT NULL,
  owner_name          VARCHAR(50)    NOT NULL,
  phone               VARCHAR(20)    NOT NULL,
  work_unit           VARCHAR(100),
  family_size         INT            DEFAULT 1,
  repair_fund_balance DECIMAL(12,2)  DEFAULT 0,
  status              TINYINT        DEFAULT 1,
  check_in_date       DATE,
  check_out_date      DATE,
  create_time         DATETIME       DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (household_id),
  KEY idx_room (building_no, floor_no, unit_no),
  KEY idx_owner_name (owner_name),
  KEY idx_phone      (phone),
  KEY idx_status     (status),
  CONSTRAINT fk_household_building FOREIGN KEY (building_no)
    REFERENCES building(building_no) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT chk_household_area    CHECK (area > 0),
  CONSTRAINT chk_household_rate    CHECK (property_fee_rate > 0),
  CONSTRAINT chk_household_status  CHECK (status IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='住户信息表';

-- --------------------------------------
-- 2.3 维修员信息表
-- --------------------------------------
CREATE TABLE maintenance_staff (
  worker_no   VARCHAR(20)    NOT NULL,
  real_name   VARCHAR(50)    NOT NULL,
  phone       VARCHAR(20)   ,
  status      TINYINT        DEFAULT 1,
  create_time DATETIME       DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (worker_no),
  CONSTRAINT chk_staff_status CHECK (status IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='维修员信息表';

-- --------------------------------------
-- 2.4 用户登录表（纯认证）
-- ref_id 按角色指向不同表：resident → household_id, maintenance → worker_no
-- --------------------------------------
CREATE TABLE user (
  user_id       INT            AUTO_INCREMENT,
  username      VARCHAR(50)    NOT NULL,
  password      VARCHAR(200)   NOT NULL,
  role          ENUM('admin','maintenance','resident') NOT NULL,
  ref_id        VARCHAR(50)   ,            -- resident=住户ID, maintenance=维修员工号
  status        TINYINT        DEFAULT 1,
  create_time   DATETIME       DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  UNIQUE KEY uk_username (username),
  CONSTRAINT chk_user_status CHECK (status IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户登录表（纯认证）';

-- --------------------------------------
-- 2.4 物业费缴费记录表（按月）
-- --------------------------------------
CREATE TABLE property_fee (
  fee_id        INT            AUTO_INCREMENT,
  household_id  INT            NOT NULL,
  year          INT            NOT NULL,
  month         INT            NOT NULL,
  amount        DECIMAL(10,2)  NOT NULL,
  is_paid       TINYINT        DEFAULT 0,
  pay_date      DATE          ,
  handler       VARCHAR(50)   ,
  bill_no       VARCHAR(50)   ,
  PRIMARY KEY (fee_id),
  UNIQUE KEY uk_household_year_month (household_id, year, month),
  KEY idx_fee_paid  (is_paid, year, month),
  KEY idx_bill_no   (bill_no),
  CONSTRAINT fk_property_fee_household FOREIGN KEY (household_id)
    REFERENCES household(household_id) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT chk_property_fee_amount CHECK (amount > 0),
  CONSTRAINT chk_property_fee_month  CHECK (month BETWEEN 1 AND 12),
  CONSTRAINT chk_property_fee_paid   CHECK (is_paid IN (-1, 0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物业费缴费记录表';

-- --------------------------------------
-- 2.5 停车位信息表（space_no 直接做主键）
-- --------------------------------------
CREATE TABLE parking_space (
  space_no      VARCHAR(20)    NOT NULL,
  household_id  INT           ,
  plate_no      VARCHAR(20)   ,
  monthly_fee   DECIMAL(10,2)  NOT NULL,
  status        TINYINT        DEFAULT 1,
  create_time   DATETIME       DEFAULT CURRENT_TIMESTAMP,
  assigned_date DATE          DEFAULT NULL  COMMENT '当前租户分配日期，释放时清空',
  PRIMARY KEY (space_no),
  KEY idx_parking_household (household_id),
  CONSTRAINT fk_parking_space_household FOREIGN KEY (household_id)
    REFERENCES household(household_id) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT chk_parking_space_fee    CHECK (monthly_fee > 0),
  CONSTRAINT chk_parking_space_status CHECK (status IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='停车位信息表';

-- --------------------------------------
-- 2.6 停车费缴费记录表（按月）
-- household_id 在生成费用时从 parking_space 复制，冻结历史关联
-- --------------------------------------
CREATE TABLE parking_fee (
  fee_id        INT            AUTO_INCREMENT,
  space_no      VARCHAR(20)    NOT NULL,
  household_id  INT            NOT NULL,
  year          INT            NOT NULL,
  month         INT            NOT NULL,
  amount        DECIMAL(10,2)  NOT NULL,
  is_paid       TINYINT        DEFAULT 0,
  pay_date      DATE          ,
  handler       VARCHAR(50)   ,
  bill_no       VARCHAR(50)   ,
  PRIMARY KEY (fee_id),
  UNIQUE KEY uk_space_year_month (space_no, year, month),
  KEY idx_pfee_paid (is_paid, year, month),
  KEY idx_pfee_bill (bill_no),
  KEY idx_pfee_household (household_id),
  CONSTRAINT fk_parking_fee_space FOREIGN KEY (space_no)
    REFERENCES parking_space(space_no) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT fk_parking_fee_household FOREIGN KEY (household_id)
    REFERENCES household(household_id) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT chk_parking_fee_amount CHECK (amount > 0),
  CONSTRAINT chk_parking_fee_month  CHECK (month BETWEEN 1 AND 12),
  CONSTRAINT chk_parking_fee_paid   CHECK (is_paid IN (-1, 0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='停车费缴费记录表';

-- --------------------------------------
-- 2.7 维修信息表
-- --------------------------------------
CREATE TABLE repair (
  repair_id     INT            AUTO_INCREMENT,
  household_id  INT            NOT NULL,
  content       VARCHAR(500)   NOT NULL,
  report_date   DATE           NOT NULL,
  repair_date   DATE          ,
  amount        DECIMAL(10,2)  DEFAULT 0,
  is_from_fund  TINYINT        DEFAULT 0,
  repair_person VARCHAR(50)   ,
  status        TINYINT        DEFAULT 0,
  create_time   DATETIME       DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (repair_id),
  KEY idx_repair_household (household_id),
  KEY idx_repair_status    (status),
  KEY idx_report_date      (report_date),
  CONSTRAINT fk_repair_household FOREIGN KEY (household_id)
    REFERENCES household(household_id) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT chk_repair_amount      CHECK (amount >= 0),
  CONSTRAINT chk_repair_is_fund     CHECK (is_from_fund IN (0, 1)),
  CONSTRAINT chk_repair_status      CHECK (status IN (0, 1, 2))
  -- 注意：repair_date >= report_date 通过触发器校验，因为 MySQL CHECK 不支持子查询
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='维修信息表';

-- --------------------------------------
-- 2.8 文件附件表（报修图片等）
-- --------------------------------------
CREATE TABLE attachment (
  id            BIGINT         AUTO_INCREMENT,
  related_type  VARCHAR(20)    NOT NULL COMMENT '关联类型（如 repair）',
  related_id    INT            NOT NULL COMMENT '关联记录ID',
  file_name     VARCHAR(100)   NOT NULL COMMENT '存储文件名（UUID）',
  original_name VARCHAR(200)   NOT NULL COMMENT '原始文件名',
  file_path     VARCHAR(500)   NOT NULL COMMENT '存储相对路径',
  file_size     BIGINT         NOT NULL COMMENT '文件大小（字节）',
  content_type  VARCHAR(100)   COMMENT 'MIME类型',
  create_time   DATETIME       DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_attachment_related (related_type, related_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件附件表';

-- --------------------------------------
-- 2.9 系统配置表（持久化截止日等运行时配置）
-- --------------------------------------
CREATE TABLE IF NOT EXISTS system_config (
  config_key   VARCHAR(50)    PRIMARY KEY,
  config_value VARCHAR(100)   NOT NULL,
  update_time  DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';


-- ============================================================
-- 3. 触发器
-- ============================================================

-- --------------------------------------
-- 3.1 维修日期校验：repair_date >= report_date（允许当天完成）
-- --------------------------------------
DELIMITER //

CREATE TRIGGER tr_repair_before_update
BEFORE UPDATE ON repair
FOR EACH ROW
BEGIN
  IF NEW.repair_date IS NOT NULL AND NEW.repair_date < NEW.report_date THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = '维修日期不能早于报修日期';
  END IF;
END//

DELIMITER ;

-- --------------------------------------
-- 3.2 维修完成后，自动扣减维修基金余额
-- --------------------------------------
DELIMITER //

CREATE TRIGGER tr_repair_after_update
AFTER UPDATE ON repair
FOR EACH ROW
BEGIN
  -- 从未完成变为已完成 且 从维修基金支出
  IF NEW.status = 1 AND OLD.status <> 1 AND NEW.is_from_fund = 1 AND NEW.amount > 0 THEN
    UPDATE household
    SET repair_fund_balance = repair_fund_balance - NEW.amount
    WHERE household_id = NEW.household_id;
  END IF;
END//

DELIMITER ;

-- --------------------------------------
-- 3.3 停车位分配/释放时自动联动状态
-- --------------------------------------
DELIMITER //

CREATE TRIGGER tr_parking_space_before_update
BEFORE UPDATE ON parking_space
FOR EACH ROW
BEGIN
  IF NEW.household_id IS NOT NULL THEN
    SET NEW.status = 1;       -- 已租
  ELSE
    SET NEW.status = 0;       -- 空闲
    SET NEW.plate_no = NULL;  -- 清空车牌
  END IF;
END//

DELIMITER ;

-- --------------------------------------
-- 3.4 停车位插入时自动设置状态
-- --------------------------------------
DELIMITER //

CREATE TRIGGER tr_parking_space_before_insert
BEFORE INSERT ON parking_space
FOR EACH ROW
BEGIN
  IF NEW.household_id IS NULL THEN
    SET NEW.status = 0;
  ELSE
    SET NEW.status = 1;
  END IF;
END//

DELIMITER ;


-- ============================================================
-- 4. 存储过程
-- ============================================================

-- --------------------------------------
-- 4.1 按楼号+房号查询住户的物业费及停车费
-- 状态：-1=逾期, 0=待缴, 1=已缴
-- --------------------------------------
DELIMITER //

CREATE PROCEDURE sp_query_household_fees(
  IN p_building_no VARCHAR(10),
  IN p_floor_no    INT,
  IN p_unit_no     INT
)
BEGIN
  -- 物业费
  SELECT
    b.building_no   AS 楼号,
    CONCAT(h.floor_no, LPAD(h.unit_no, 2, '0')) AS 房号,
    h.owner_name    AS 户主,
    pf.year         AS 年份,
    pf.month        AS 月份,
    pf.amount       AS 应缴物业费,
    CASE 
      WHEN pf.is_paid = 1 THEN '已缴'
      WHEN pf.is_paid = -1 THEN '逾期'
      WHEN pf.is_paid = 0 THEN '待缴'
      ELSE '未生成'
    END AS 缴费状态,
    pf.pay_date     AS 缴费日期,
    pf.bill_no      AS 缴费单号
  FROM household h
  JOIN building b ON h.building_no = b.building_no
  LEFT JOIN property_fee pf ON h.household_id = pf.household_id
  WHERE b.building_no = p_building_no
    AND h.floor_no   = p_floor_no
    AND h.unit_no    = p_unit_no
    AND h.status     = 1
  ORDER BY pf.year DESC, pf.month DESC;

  -- 停车费
  SELECT
    ps.space_no     AS 停车位编号,
    ps.plate_no     AS 车牌号,
    pkf.year        AS 年份,
    pkf.month       AS 月份,
    pkf.amount      AS 应缴停车费,
    CASE 
      WHEN pkf.is_paid = 1 THEN '已缴'
      WHEN pkf.is_paid = -1 THEN '逾期'
      WHEN pkf.is_paid = 0 THEN '待缴'
      ELSE '未生成'
    END AS 缴费状态,
    pkf.pay_date    AS 缴费日期,
    pkf.bill_no     AS 缴费单号
  FROM household h
  JOIN building b ON h.building_no = b.building_no
  JOIN parking_space ps ON h.household_id = ps.household_id
  LEFT JOIN parking_fee pkf ON ps.space_no = pkf.space_no
  WHERE b.building_no = p_building_no
    AND h.floor_no   = p_floor_no
    AND h.unit_no    = p_unit_no
    AND h.status     = 1
  ORDER BY pkf.year DESC, pkf.month DESC;
END//

DELIMITER ;

-- --------------------------------------
-- 4.2 按年月统计小区物业费汇总
-- 状态：-1=逾期, 0=待缴, 1=已缴
-- --------------------------------------
DELIMITER //

CREATE PROCEDURE sp_calc_property_fee_summary(
  IN p_year  INT,
  IN p_month INT
)
BEGIN
  SELECT
    p_year  AS 年份,
    p_month AS 月份,
    COUNT(*) AS 总户数,
    SUM(pf.amount) AS 应缴物业费总额,
    SUM(CASE WHEN pf.is_paid = 1 THEN pf.amount ELSE 0 END) AS 已缴物业费总额,
    SUM(CASE WHEN pf.is_paid = 0 THEN pf.amount ELSE 0 END) AS 待缴物业费总额,
    SUM(CASE WHEN pf.is_paid = -1 THEN pf.amount ELSE 0 END) AS 逾期物业费总额,
    COUNT(CASE WHEN pf.is_paid = 0 THEN 1 END) AS 待缴户数,
    COUNT(CASE WHEN pf.is_paid = -1 THEN 1 END) AS 逾期户数
  FROM property_fee pf
  JOIN household h ON pf.household_id = h.household_id
  WHERE pf.year  = p_year
    AND pf.month = p_month
    AND h.status = 1;
END//

DELIMITER ;

-- --------------------------------------
-- 4.3 统计停车位总数
-- --------------------------------------
DELIMITER //

CREATE PROCEDURE sp_count_parking_spaces(OUT total INT)
BEGIN
  SELECT COUNT(*) INTO total FROM parking_space;
END//

DELIMITER ;

-- --------------------------------------
-- 4.4 统计小区在住总户数
-- --------------------------------------
DELIMITER //

CREATE PROCEDURE sp_count_households(OUT total INT)
BEGIN
  SELECT COUNT(*) INTO total FROM household WHERE status = 1;
END//

DELIMITER ;

-- --------------------------------------
-- 4.5 每月1日生成当月账单（定时任务调用）
-- 关键逻辑：通过 NOT EXISTS 检查避免重复生成
-- 若住户/车位已存在当月账单记录（包括预缴生成的记录），则跳过不重复生成
-- --------------------------------------
DELIMITER //
CREATE PROCEDURE sp_generate_monthly_bills()
BEGIN
  DECLARE current_year INT;
  DECLARE current_month INT;
  
  SELECT YEAR(CURDATE()) INTO current_year;
  SELECT MONTH(CURDATE()) INTO current_month;
  
  -- 生成物业费账单：为所有在住住户（status=1）生成当月记录
  INSERT INTO property_fee (household_id, year, month, amount, is_paid)
  SELECT h.household_id, current_year, current_month,
         h.area * h.property_fee_rate, 0
  FROM household h
  WHERE h.status = 1
    AND NOT EXISTS (
      SELECT 1 FROM property_fee pf
      WHERE pf.household_id = h.household_id
        AND pf.year = current_year
        AND pf.month = current_month
    );
  
  -- 生成停车费账单：为所有已租车位（status=1）生成当月记录
  INSERT INTO parking_fee (space_no, household_id, year, month, amount, is_paid)
  SELECT ps.space_no, ps.household_id, current_year, current_month,
         ps.monthly_fee, 0
  FROM parking_space ps
  WHERE ps.status = 1
    AND ps.household_id IS NOT NULL
    AND NOT EXISTS (
      SELECT 1 FROM parking_fee pf
      WHERE pf.space_no = ps.space_no
        AND pf.year = current_year
        AND pf.month = current_month
    );
END//
DELIMITER ;

-- --------------------------------------
-- 4.6 每天检查逾期并更新状态（定时任务调用）
-- 只有当天是截止日后的第一天时才执行逾期标记
-- --------------------------------------
DELIMITER //
CREATE PROCEDURE sp_mark_overdue(IN p_deadline_day INT)
BEGIN
  DECLARE current_day INT;
  
  SELECT DAY(CURDATE()) INTO current_day;
  
  -- 只有当天是截止日后的第一天时才执行逾期标记
  IF current_day = p_deadline_day + 1 THEN
    -- 更新物业费逾期状态
    UPDATE property_fee
    SET is_paid = -1
    WHERE is_paid = 0
      AND year = YEAR(CURDATE())
      AND month = MONTH(CURDATE());
    
    -- 更新停车费逾期状态
    UPDATE parking_fee
    SET is_paid = -1
    WHERE is_paid = 0
      AND year = YEAR(CURDATE())
      AND month = MONTH(CURDATE());
  END IF;
END//
DELIMITER ;

-- --------------------------------------
-- 4.7 截止日变更后刷新本月待缴/逾期状态
-- --------------------------------------
DELIMITER //
CREATE PROCEDURE sp_refresh_after_deadline_change(IN p_new_deadline INT)
BEGIN
  DECLARE cur_year INT DEFAULT YEAR(CURDATE());
  DECLARE cur_month INT DEFAULT MONTH(CURDATE());
  -- 待缴/逾期 → 根据新截止日重新判定（已缴 is_paid=1 的不动）
  UPDATE property_fee SET is_paid = CASE
    WHEN DAY(CURDATE()) <= p_new_deadline THEN 0 ELSE -1 END
  WHERE year = cur_year AND month = cur_month AND is_paid IN (0, -1);
  UPDATE parking_fee SET is_paid = CASE
    WHEN DAY(CURDATE()) <= p_new_deadline THEN 0 ELSE -1 END
  WHERE year = cur_year AND month = cur_month AND is_paid IN (0, -1);
END//
DELIMITER ;


-- ============================================================
-- 5. 插入测试数据
-- ============================================================

-- 楼栋
INSERT INTO building (building_no, floor_count, units_per_floor) VALUES
('28', 28, 4),
('29', 18, 3),
('30', 33, 6);

-- 住户
INSERT INTO household (household_id, building_no, floor_no, unit_no, area, property_fee_rate,
                       owner_name, phone, work_unit, family_size, repair_fund_balance,
                       status, check_in_date)
VALUES
(1, '28', 13, 2, 135.00, 2.80, '张三', '13468029999', 'IBM公司', 3, 7500.00, 1, '2026-01-15'),
(2, '28', 5,  1,  90.00, 2.80, '李四', '14368130001', '阿里巴巴', 2, 5000.00, 1, '2026-10-01'),
(3, '29', 8,  3, 120.00, 2.50, '王五', '15668250002', '腾讯公司', 4, 10000.00, 1, '2026-01-10');

-- 维修员
INSERT INTO maintenance_staff (worker_no, real_name, phone, status) VALUES
('WX001', '维修员老刘', '15468990001', 1),
('WX002', '维修员小陈', '16768990002', 1);

-- 用户（密码均为 BCrypt 加密后的 "123456"）
-- BCrypt 密文需运行 PasswordGen 生成后替换
INSERT INTO user (user_id, username, password, role, ref_id, status) VALUES
(1, 'admin',    '$2a$10$MW6vgJuUrBrM1PZfJd9fH.FCRNFrKs1BjMHQT01JfB.jHJbYl8fmO', 'admin',       NULL,    1),
(2, 'zhangsan', '$2a$10$MW6vgJuUrBrM1PZfJd9fH.FCRNFrKs1BjMHQT01JfB.jHJbYl8fmO', 'resident',    '1',     1),
(3, 'lisi',     '$2a$10$MW6vgJuUrBrM1PZfJd9fH.FCRNFrKs1BjMHQT01JfB.jHJbYl8fmO', 'resident',    '2',     1),
(4, 'wangwu',   '$2a$10$MW6vgJuUrBrM1PZfJd9fH.FCRNFrKs1BjMHQT01JfB.jHJbYl8fmO', 'resident',    '3',     1),
(5, 'weixiu01', '$2a$10$MW6vgJuUrBrM1PZfJd9fH.FCRNFrKs1BjMHQT01JfB.jHJbYl8fmO', 'maintenance', 'WX001', 1),
(6, 'weixiu02', '$2a$10$MW6vgJuUrBrM1PZfJd9fH.FCRNFrKs1BjMHQT01JfB.jHJbYl8fmO', 'maintenance', 'WX002', 1);

-- 物业费
-- 张三：135㎡ × 2.80 = 378元/月
INSERT INTO property_fee (household_id, year, month, amount, is_paid, pay_date, handler, bill_no)
VALUES
-- 张三 2026年
(1, 2026,  1, 378.00, 1, '2026-01-05', '系统管理员', 'WY20260105001'),
(1, 2026,  2, 378.00, 1, '2026-01-05', '系统管理员', 'WY20260105001'),
(1, 2026,  3, 378.00, 1, '2026-01-05', '系统管理员', 'WY20260105001'),
(1, 2026,  4, 378.00, 1, '2026-01-05', '系统管理员', 'WY20260105001'),
(1, 2026,  5, 378.00, 1, '2026-01-05', '系统管理员', 'WY20260105001'),
(1, 2026,  6, 378.00, 1, '2026-01-05', '系统管理员', 'WY20260105001'),
(1, 2026,  7, 378.00, 0, null, null, null),
-- 王五 2026年（1~6月已缴）120㎡ × 2.50 = 300元/月
(3, 2026,  1, 300.00, 1, '2026-01-03', '系统管理员', 'WY20260103001'),
(3, 2026,  2, 300.00, 1, '2026-01-03', '系统管理员', 'WY20260103001'),
(3, 2026,  3, 300.00, 1, '2026-01-03', '系统管理员', 'WY20260103001'),
(3, 2026,  4, 300.00, 1, '2026-01-03', '系统管理员', 'WY20260103001'),
(3, 2026,  5, 300.00, 1, '2026-01-03', '系统管理员', 'WY20260103001'),
(3, 2026,  6, 300.00, 1, '2026-01-03', '系统管理员', 'WY20260103001'),
(3, 2026,  7, 300.00, 0, null, null, null);

-- 停车位
INSERT INTO parking_space (space_no, household_id, plate_no, monthly_fee, status, assigned_date)
VALUES
('A001', 1, '京A12345', 300.00, 1, '2026-01-01'),
('A002', 3, '京B67890', 300.00, 1, '2026-01-01'),
('A003', NULL, NULL, 350.00, 0, NULL),
('B001', NULL, NULL, 280.00, 0, NULL);

-- 停车费
INSERT INTO parking_fee (fee_id, space_no, household_id, year, month, amount, is_paid, pay_date, handler, bill_no)
VALUES
-- 车位 A001（张三，household_id=1）：1~6月已缴
(1,  'A001', 1, 2026,  1, 300.00, 1, '2026-01-05', '系统管理员', 'TC20260105001'),
(2,  'A001', 1, 2026,  2, 300.00, 1, '2026-01-05', '系统管理员', 'TC20260105001'),
(3,  'A001', 1, 2026,  3, 300.00, 1, '2026-01-05', '系统管理员', 'TC20260105001'),
(4,  'A001', 1, 2026,  4, 300.00, 1, '2026-01-05', '系统管理员', 'TC20260105001'),
(5,  'A001', 1, 2026,  5, 300.00, 1, '2026-01-05', '系统管理员', 'TC20260105001'),
(6,  'A001', 1, 2026,  6, 300.00, 1, '2026-01-05', '系统管理员', 'TC20260105001'),
(7,  'A001', 1, 2026,  7, 300.00, 0, null, null, null),
-- 车位 A002（王五，household_id=3）：1~4月已缴
(13, 'A002', 3, 2026,  1, 300.00, 1, '2026-01-03', '系统管理员', 'TC20260103001'),
(14, 'A002', 3, 2026,  2, 300.00, 1, '2026-01-03', '系统管理员', 'TC20260103002'),
(15, 'A002', 3, 2026,  3, 300.00, 1, '2026-01-03', '系统管理员', 'TC20260103003'),
(16, 'A002', 3, 2026,  4, 300.00, 1, '2026-01-03', '系统管理员', 'TC20260103004'),
(17, 'A002', 3, 2026,  5, 300.00, 0, null, null, null),
(18, 'A002', 3, 2026,  6, 300.00, 0, null, null, null),
(19, 'A002', 3, 2026,  7, 300.00, 0, null, null, null);

-- 维修记录
INSERT INTO repair (repair_id, household_id, content, report_date, repair_date, amount, is_from_fund, repair_person, status)
VALUES
(1, 1, '卫生间水管漏水',       '2026-05-10', '2026-05-12', 200.00, 0, '维修员老刘', 1),  -- 已完成，自费
(2, 1, '客厅墙面开裂',         '2026-06-20', NULL,             0,    0, NULL,         0),  -- 待维修
(3, 2, '厨房下水道堵塞',       '2026-12-05', NULL,             0,    0, NULL,         0),  -- 待维修
(4, 3, '空调不制冷',           '2026-04-15', '2026-04-15', 500.00, 1, '维修员小陈', 1),  -- 已完成，基金支出
(5, 3, '阳台门锁损坏',         '2026-08-01', NULL,             0,    0, NULL,         2);  -- 已取消


-- ============================================================
-- 5.x 初始化系统配置 + 存量数据迁移
-- ============================================================
-- 截止日默认每月10号
INSERT INTO system_config (config_key, config_value) VALUES ('deadline_day', '10')
  ON DUPLICATE KEY UPDATE config_value = VALUES(config_value);

-- 存量数据：已过去但未缴的标记为逾期（is_paid = -1）
UPDATE property_fee SET is_paid = -1
  WHERE is_paid = 0
    AND (year < YEAR(CURDATE())
         OR (year = YEAR(CURDATE()) AND month < MONTH(CURDATE()))
         OR (year = YEAR(CURDATE()) AND month = MONTH(CURDATE()) AND DAY(CURDATE()) > 10));

UPDATE parking_fee SET is_paid = -1
  WHERE is_paid = 0
    AND (year < YEAR(CURDATE())
         OR (year = YEAR(CURDATE()) AND month < MONTH(CURDATE()))
         OR (year = YEAR(CURDATE()) AND month = MONTH(CURDATE()) AND DAY(CURDATE()) > 10));

-- ============================================================
-- 6. 验证
-- ============================================================

-- 查看所有表
SHOW TABLES;

-- 每条表记录数
SELECT 'building'      AS table_name, COUNT(*) AS cnt FROM building
UNION ALL
SELECT 'household',    COUNT(*) FROM household
UNION ALL
SELECT 'user',         COUNT(*) FROM user
UNION ALL
SELECT 'property_fee', COUNT(*) FROM property_fee
UNION ALL
SELECT 'parking_space',COUNT(*) FROM parking_space
UNION ALL
SELECT 'parking_fee',  COUNT(*) FROM parking_fee
UNION ALL
SELECT 'repair',       COUNT(*) FROM repair;

-- 测试存储过程
CALL sp_count_households(@total);
SELECT @total AS 在住总户数;

CALL sp_count_parking_spaces(@total);
SELECT @total AS 停车位总数;

CALL sp_calc_property_fee_summary(2026, 7);

CALL sp_query_household_fees('28', 13, 2);

