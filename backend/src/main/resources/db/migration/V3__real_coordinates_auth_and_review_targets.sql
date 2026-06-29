ALTER TABLE activities MODIFY longitude DECIMAL(10,6) NOT NULL DEFAULT 120.155070;
ALTER TABLE activities MODIFY latitude DECIMAL(10,6) NOT NULL DEFAULT 30.274085;

INSERT IGNORE INTO users (id, email, password_hash, nickname, avatar, role, city, bio, interests, following_count, follower_count, credit, verified, activated, status, merchant_name)
VALUES ('admin','admin@quju.cn','plain:Admin123456','周晴','https://i.pravatar.cc/160?img=11','管理员','杭州','平台运营管理员','运营,审核',0,0,100,1,1,'正常',NULL);

UPDATE activities SET longitude=120.139863, latitude=30.318332, start_at='2026-06-30 18:30:00', end_at='2026-06-30 21:00:00', registration_deadline='2026-06-30 17:00:00' WHERE id='act-001';
UPDATE activities SET longitude=120.119235, latitude=30.209840, start_at='2026-07-02 09:00:00', end_at='2026-07-02 14:30:00', registration_deadline='2026-07-01 20:00:00' WHERE id='act-002';
UPDATE activities SET longitude=120.168929, latitude=30.255672, start_at='2026-07-04 19:00:00', end_at='2026-07-04 22:30:00', registration_deadline='2026-07-04 18:00:00' WHERE id='act-003';
UPDATE activities SET longitude=120.146515, latitude=30.231297, start_at='2026-07-05 07:30:00', end_at='2026-07-05 10:00:00', registration_deadline='2026-07-04 22:00:00' WHERE id='act-004';
UPDATE activities SET longitude=120.193900, latitude=30.209600, start_at='2026-07-06 14:00:00', end_at='2026-07-06 16:00:00', registration_deadline='2026-07-06 12:00:00' WHERE id='act-005';
UPDATE activities SET longitude=120.121900, latitude=30.283300, start_at='2026-07-08 13:30:00', end_at='2026-07-08 16:30:00', registration_deadline='2026-07-08 11:00:00' WHERE id='act-006';

INSERT IGNORE INTO activities (id,title,summary,description,category,cover,date_label,time_label,start_at,end_at,registration_deadline,location,district,distance,longitude,latitude,price,capacity,joined_count,status,organizer_id,featured,safety_note,min_age,join_fields)
VALUES
('act-pending-001','千岛湖夜间桨板体验','夜间水上体验，需要重点核查安全预案。','活动计划在千岛湖开展夜间桨板体验，包含救生装备、保险和天气取消方案，提交人工审核。','户外运动','https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=1200&q=85','07月12日','19:00 - 22:00','2026-07-12 19:00:00','2026-07-12 22:00:00','2026-07-10 20:00:00','千岛湖中心湖区','淳安县',142.0,119.051900,29.608800,199,68,0,'审核中','u-002',0,'水上活动需全程穿救生衣，恶劣天气取消。',18,'真实姓名,手机号码,紧急联系人'),
('act-pending-002','凌晨天文观测小队','夜间偏远地点观星活动，需要补充集合返程方案。','活动计划凌晨前往郊外观星，因夜间和偏远地点触发人工审核。','学习交流','https://images.unsplash.com/photo-1444703686981-a3abbc4d4fe3?auto=format&fit=crop&w=1200&q=85','07月13日','01:00 - 04:00','2026-07-13 01:00:00','2026-07-13 04:00:00','2026-07-11 20:00:00','临安青山湖观景台','临安区',45.0,119.802900,30.253500,59,28,0,'审核中','u-003',0,'需确认夜间照明、返程交通和紧急联系人。',18,'真实姓名,手机号码,紧急联系人');

INSERT IGNORE INTO activity_tags (activity_id, tag, rank_order)
VALUES
('act-pending-001','水上',1),('act-pending-001','夜间',2),('act-pending-001','人工审核',3),
('act-pending-002','天文',1),('act-pending-002','夜间',2),('act-pending-002','安全预案',3);

INSERT IGNORE INTO merchant_applications (id,user_id,merchant_name,license_name,status,reason)
VALUES ('merchant-001','u-004','流木咖啡（湖滨店）','liumu-license.pdf','待审核',NULL);
