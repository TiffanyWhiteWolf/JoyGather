ALTER TABLE activities ADD COLUMN city VARCHAR(80) NOT NULL DEFAULT '杭州' AFTER location;

UPDATE activities
SET city = CASE
    WHEN location LIKE '%北京%' OR (longitude BETWEEN 115 AND 118 AND latitude BETWEEN 39 AND 41.5) THEN '北京'
    ELSE '杭州'
END;

INSERT IGNORE INTO activities
(id,title,summary,description,category,cover,date_label,time_label,start_at,end_at,registration_deadline,location,city,district,distance,longitude,latitude,price,capacity,joined_count,status,organizer_id,featured,safety_note,min_age,join_fields,published_at,visibility)
VALUES
('bj-act-001','奥森晚风跑｜5 公里轻松局','不卷配速，沿森林公园慢跑一圈，再一起拉伸聊天','从奥林匹克森林公园南门集合，按舒适配速分组完成 5 公里。新手可以跑走结合，领队会在每个路口等待。','运动健身','https://images.unsplash.com/photo-1552674605-db6ffd4facb5?auto=format&fit=crop&w=1200&q=85','07月07日','18:30 - 20:30','2026-07-07 18:30:00','2026-07-07 20:30:00','2026-07-07 16:00:00','奥林匹克森林公园南门','北京','朝阳区',3.2,116.392891,40.015120,0,30,14,'报名中','u-001',1,'请穿适合跑步的鞋并自备饮水，身体不适请及时告知领队。',16,'真实姓名,手机号码,紧急联系人',NOW(),'PUBLIC'),
('bj-act-002','北京坊建筑摄影漫步','从老城肌理走到当代街区，用镜头记录中轴线旁的光影','路线从北京坊出发，经过前门周边历史建筑。无需专业相机，手机也可以参加。','城市探索','https://images.unsplash.com/photo-1508804185872-d7badad00f7d?auto=format&fit=crop&w=1200&q=85','07月09日','16:00 - 19:00','2026-07-09 16:00:00','2026-07-09 19:00:00','2026-07-09 12:00:00','北京坊东区广场','北京','西城区',4.6,116.397910,39.898215,19,20,11,'报名中','u-003',1,'步行路线约 4 公里，请注意防晒并遵守公共场所拍摄规则。',16,'真实姓名,手机号码',NOW(),'PUBLIC'),
('bj-act-003','国家图书馆共读与交换','带一本最近读完的书，交换一段意外遇见的文字','先进行一小时安静共读，再用三分钟介绍自己的书，最后自由交换。','学习交流','https://images.unsplash.com/photo-1526243741027-444d633d7365?auto=format&fit=crop&w=1200&q=85','07月11日','14:00 - 17:00','2026-07-11 14:00:00','2026-07-11 17:00:00','2026-07-11 10:00:00','国家图书馆总馆南区东门','北京','海淀区',6.1,116.325190,39.943047,0,18,9,'报名中','u-003',0,'请保持安静并遵守馆内规定，交换图书前确认双方意愿。',12,'真实姓名,手机号码',NOW(),'PUBLIC'),
('bj-act-004','798 艺术区周末速写局','不比画技，在展馆与街巷里完成自己的城市速写页','提供简单的观察与构图提示，可自带画具，也可领取基础纸笔。','城市探索','https://images.unsplash.com/photo-1545987796-200677ee1011?auto=format&fit=crop&w=1200&q=85','07月12日','10:00 - 13:00','2026-07-12 10:00:00','2026-07-12 13:00:00','2026-07-11 20:00:00','798 艺术区南门','北京','朝阳区',8.4,116.495570,39.984110,29,16,7,'报名中','u-004',0,'户外活动请注意防晒，进入展馆时遵守现场管理要求。',12,'真实姓名,手机号码',NOW(),'PUBLIC');

INSERT IGNORE INTO activity_tags (activity_id, tag, rank_order) VALUES
('bj-act-001','夜跑',1),('bj-act-001','新手友好',2),('bj-act-001','奥森',3),
('bj-act-002','Citywalk',1),('bj-act-002','建筑',2),('bj-act-002','摄影',3),
('bj-act-003','共读',1),('bj-act-003','换书',2),('bj-act-003','安静社交',3),
('bj-act-004','速写',1),('bj-act-004','艺术',2),('bj-act-004','周末',3);
