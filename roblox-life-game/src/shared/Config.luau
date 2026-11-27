-- 인생 게임 설정 (서버와 클라이언트가 함께 사용)
local Config = {}

-- 시간 / 기본값
Config.SECONDS_PER_YEAR = 20 -- 실제 20초 = 게임 속 1년
Config.START_MONEY = 100
Config.MIN_LIFESPAN = 70
Config.MAX_LIFESPAN = 100
Config.INHERITANCE_RATE = 0.1 -- 환생할 때 이전 인생 점수의 10%를 유산으로 받음
Config.ALLOWANCE = 30 -- 6~19살 동안 매년 받는 용돈
Config.STUDY_MIN_AGE = 6
Config.STUDY_MAX_AGE = 40
Config.HOSPITAL_COST = 150
Config.EVENT_CHANCE = 0.35 -- 매년 랜덤 이벤트가 일어날 확률
Config.WORLD_RADIUS = 75

-- 행동별 쿨타임 (초)
Config.Cooldowns = {
	Study = 6,
	Work = 6,
	Hospital = 10,
	Park = 12,
}

-- 인생 단계 (scale = 캐릭터 크기)
Config.Stages = {
	{ name = "아기", minAge = 0, scale = 0.55, walkSpeed = 10 },
	{ name = "어린이", minAge = 6, scale = 0.7, walkSpeed = 18 },
	{ name = "청소년", minAge = 13, scale = 0.88, walkSpeed = 18 },
	{ name = "청년", minAge = 20, scale = 1, walkSpeed = 16 },
	{ name = "중년", minAge = 40, scale = 1, walkSpeed = 15 },
	{ name = "노인", minAge = 65, scale = 0.95, walkSpeed = 11 },
}

-- 직업 (salary = 한 번 일할 때 받는 돈)
Config.Jobs = {
	{ id = "parttime", name = "🏪 편의점 알바", salary = 40, minAge = 15, education = 0 },
	{ id = "delivery", name = "📦 택배 기사", salary = 70, minAge = 18, education = 5 },
	{ id = "barista", name = "☕ 바리스타", salary = 90, minAge = 18, education = 15 },
	{ id = "police", name = "👮 경찰관", salary = 160, minAge = 21, education = 35 },
	{ id = "teacher", name = "👩‍🏫 선생님", salary = 200, minAge = 23, education = 50 },
	{ id = "programmer", name = "💻 프로그래머", salary = 320, minAge = 22, education = 65 },
	{ id = "lawyer", name = "⚖️ 변호사", salary = 450, minAge = 26, education = 80 },
	{ id = "doctor", name = "🩺 의사", salary = 600, minAge = 28, education = 95 },
}

-- 상점 물건 (happiness = 보유 시 매년 행복도에 주는 보너스)
Config.Items = {
	{ id = "console", name = "🎮 게임기", category = "취미", price = 500, happiness = 6, minAge = 6 },
	{ id = "bike", name = "🚲 자전거", category = "탈것", price = 300, happiness = 4, minAge = 6 },
	{ id = "dog", name = "🐶 강아지", category = "반려동물", price = 800, happiness = 10, minAge = 6 },
	{ id = "cat", name = "🐱 고양이", category = "반려동물", price = 800, happiness = 10, minAge = 6 },
	{ id = "scooter", name = "🛵 스쿠터", category = "탈것", price = 1500, happiness = 8, minAge = 16 },
	{ id = "studio", name = "🏠 원룸", category = "집", price = 4000, happiness = 10, minAge = 20 },
	{ id = "car", name = "🚗 자동차", category = "탈것", price = 6000, happiness = 15, minAge = 20 },
	{ id = "house", name = "🏡 주택", category = "집", price = 20000, happiness = 25, minAge = 25 },
	{ id = "sportscar", name = "🏎️ 스포츠카", category = "탈것", price = 25000, happiness = 30, minAge = 20 },
	{ id = "mansion", name = "🏰 대저택", category = "집", price = 80000, happiness = 50, minAge = 30 },
}

-- 마을 건물 (action = 서버에서 처리할 행동)
Config.Places = {
	{ id = "School", name = "🏫 학교", prompt = "공부하기", action = "Study",
		color = Color3.fromRGB(230, 190, 120), size = Vector3.new(36, 22, 24) },
	{ id = "Office", name = "🏢 회사", prompt = "일하기", action = "Work",
		color = Color3.fromRGB(140, 160, 190), size = Vector3.new(28, 40, 24) },
	{ id = "JobCenter", name = "📋 직업 센터", prompt = "직업 찾기", action = "OpenJobs",
		color = Color3.fromRGB(120, 190, 160), size = Vector3.new(26, 18, 20) },
	{ id = "Shop", name = "🛒 쇼핑몰", prompt = "쇼핑하기", action = "OpenShop",
		color = Color3.fromRGB(240, 150, 170), size = Vector3.new(32, 18, 24) },
	{ id = "Hospital", name = "🏥 병원", prompt = "치료받기", action = "Hospital",
		color = Color3.fromRGB(245, 245, 245), size = Vector3.new(30, 26, 24) },
	{ id = "Park", name = "🌳 공원 쉼터", prompt = "쉬기", action = "Park",
		color = Color3.fromRGB(150, 210, 120), size = Vector3.new(24, 14, 18) },
}

function Config.getStage(age)
	local current = Config.Stages[1]
	for _, stage in ipairs(Config.Stages) do
		if age >= stage.minAge then
			current = stage
		end
	end
	return current
end

function Config.getJob(id)
	for _, job in ipairs(Config.Jobs) do
		if job.id == id then
			return job
		end
	end
	return nil
end

function Config.getItem(id)
	for _, item in ipairs(Config.Items) do
		if item.id == id then
			return item
		end
	end
	return nil
end

function Config.formatMoney(n)
	local formatted = tostring(math.floor(n))
	local k
	repeat
		formatted, k = formatted:gsub("^(-?%d+)(%d%d%d)", "%1,%2")
	until k == 0
	return formatted .. "원"
end

return Config
