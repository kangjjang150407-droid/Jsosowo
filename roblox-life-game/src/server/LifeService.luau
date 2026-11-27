-- 인생 게임 핵심 로직: 나이 먹기, 행동, 랜덤 이벤트, 죽음과 환생
local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Config = require(ReplicatedStorage:WaitForChild("Shared"):WaitForChild("Config"))
local DataManager = require(script.Parent.DataManager)

local LifeService = {}

local states = {} -- [Player] = 인생 상태
local cooldowns = {} -- [Player] = { [행동] = 마지막 시각 }
local remotes

local function clamp(v)
	return math.clamp(v, 0, 100)
end

local function newLife(prev)
	prev = prev or {}
	return {
		Age = 0,
		Money = prev.Inheritance or Config.START_MONEY,
		Health = 100,
		Happiness = 70,
		Education = 0,
		Job = nil,
		Owned = {},
		MaxAge = math.random(Config.MIN_LIFESPAN, Config.MAX_LIFESPAN),
		YearProgress = 0,
		Alive = true,
		Lives = (prev.Lives or 0) + 1,
		BestScore = prev.BestScore or 0,
		DeathReason = nil,
		FinalScore = nil,
	}
end

local function itemBonus(s)
	local total = 0
	for id in pairs(s.Owned) do
		local item = Config.getItem(id)
		if item then
			total += item.happiness
		end
	end
	return total
end

local function lifeScore(s)
	local assets = 0
	for id in pairs(s.Owned) do
		local item = Config.getItem(id)
		if item then
			assets += item.price
		end
	end
	return math.floor(s.Money + assets + s.Education * 20 + s.Happiness * 10 + s.Age * 5)
end

local function notify(player, text, kind)
	remotes.Notify:FireClient(player, text, kind or "info")
end

local function sync(player)
	local s = states[player]
	if not s then
		return
	end
	local leaderstats = player:FindFirstChild("leaderstats")
	if leaderstats then
		leaderstats["나이"].Value = s.Age
		leaderstats["돈"].Value = s.Money
	end
	remotes.State:FireClient(player, s)
end

-- 나이에 맞게 캐릭터 크기와 속도 조절
local function applyBody(player)
	local s = states[player]
	local character = player.Character
	if not (s and character) then
		return
	end
	local stage = Config.getStage(s.Age)
	pcall(function()
		if math.abs(character:GetScale() - stage.scale) > 0.01 then
			character:ScaleTo(stage.scale)
		end
	end)
	local humanoid = character:FindFirstChildOfClass("Humanoid")
	if humanoid then
		humanoid.WalkSpeed = s.Alive and stage.walkSpeed or 0
	end
end

---------------------------------------------------------------------
-- 랜덤 이벤트
---------------------------------------------------------------------
local EVENTS = {
	{ weight = 5, text = "💵 길에서 돈을 주웠다! +%s",
		cond = function(s) return s.Age >= 6 end,
		apply = function(s) local v = math.random(20, 200); s.Money += v; return Config.formatMoney(v) end },
	{ weight = 5, text = "🤧 감기에 걸렸다... 건강 -%s",
		apply = function(s) local v = math.random(5, 15); s.Health = clamp(s.Health - v); return v end },
	{ weight = 4, text = "🎉 친구 생일 파티에 다녀왔다! 행복 +%s",
		cond = function(s) return s.Age >= 4 and s.Age <= 30 end,
		apply = function(s) local v = math.random(5, 12); s.Happiness = clamp(s.Happiness + v); return v end },
	{ weight = 3, text = "💯 시험에서 100점을 받았다! 학력 +%s",
		cond = function(s) return s.Age >= 6 and s.Age < 20 end,
		apply = function(s) local v = 5; s.Education = clamp(s.Education + v); return v end },
	{ weight = 3, text = "📈 일을 잘해서 보너스를 받았다! +%s",
		cond = function(s) return s.Job ~= nil end,
		apply = function(s)
			local job = Config.getJob(s.Job)
			local v = (job and job.salary or 50) * 3
			s.Money += v
			return Config.formatMoney(v)
		end },
	{ weight = 3, text = "😱 지갑을 잃어버렸다... -%s",
		cond = function(s) return s.Age >= 13 and s.Money > 0 end,
		apply = function(s) local v = math.min(s.Money, math.random(50, 500)); s.Money -= v; return Config.formatMoney(v) end },
	{ weight = 3, text = "🏃 운동을 시작했다! 건강 +%s",
		cond = function(s) return s.Age >= 10 end,
		apply = function(s) local v = 10; s.Health = clamp(s.Health + v); return v end },
	{ weight = 2, text = "💕 연애를 시작했다! 행복 +%s",
		cond = function(s) return s.Age >= 18 and s.Age <= 60 end,
		apply = function(s) local v = 15; s.Happiness = clamp(s.Happiness + v); return v end },
	{ weight = 2, text = "👶 손주가 태어났다! 행복 +%s",
		cond = function(s) return s.Age >= 55 end,
		apply = function(s) local v = 20; s.Happiness = clamp(s.Happiness + v); return v end },
	{ weight = 1, text = "🚑 교통사고를 당했다... 건강 -%s",
		cond = function(s) return s.Age >= 16 end,
		apply = function(s) local v = 25; s.Health = clamp(s.Health - v); return v end },
	{ weight = 1, text = "🎰 복권 1등 당첨!!! +%s",
		cond = function(s) return s.Age >= 20 end,
		apply = function(s) local v = math.random(5, 20) * 1000; s.Money += v; return Config.formatMoney(v) end },
}

local function randomEvent(player, s)
	local pool, total = {}, 0
	for _, event in ipairs(EVENTS) do
		if not event.cond or event.cond(s) then
			table.insert(pool, event)
			total += event.weight
		end
	end
	if total == 0 then
		return
	end
	local roll = math.random() * total
	for _, event in ipairs(pool) do
		roll -= event.weight
		if roll <= 0 then
			local value = event.apply(s)
			local good = not event.text:find("%-%%s")
			notify(player, event.text:format(tostring(value)), good and "good" or "bad")
			return
		end
	end
end

---------------------------------------------------------------------
-- 죽음 / 환생
---------------------------------------------------------------------
local function die(player, reason)
	local s = states[player]
	s.Alive = false
	s.DeathReason = reason
	s.FinalScore = lifeScore(s)
	if s.FinalScore > s.BestScore then
		s.BestScore = s.FinalScore
	end
	applyBody(player)
	notify(player, ("🕊️ %d살의 나이로 생을 마감했어요. (%s)"):format(s.Age, reason), "bad")
end

local function rebirth(player)
	local s = states[player]
	if s.Alive then
		return false, "아직 살아있어요!"
	end
	local inheritance = Config.START_MONEY + math.floor((s.FinalScore or 0) * Config.INHERITANCE_RATE)
	states[player] = newLife({ Inheritance = inheritance, Lives = s.Lives, BestScore = s.BestScore })
	cooldowns[player] = {}
	applyBody(player)
	sync(player)
	return true, ("👶 새로운 인생이 시작됐어요! 유산 %s을 물려받았어요."):format(Config.formatMoney(inheritance))
end

---------------------------------------------------------------------
-- 행동
---------------------------------------------------------------------
local Actions = {}

function Actions.Study(_, s)
	if s.Age < Config.STUDY_MIN_AGE then
		return false, ("아직 너무 어려서 학교에 갈 수 없어요. (%d살부터)"):format(Config.STUDY_MIN_AGE)
	end
	if s.Age > Config.STUDY_MAX_AGE then
		return false, ("공부하기엔 너무 바빠요... (%d살까지)"):format(Config.STUDY_MAX_AGE)
	end
	if s.Education >= 100 then
		return false, "이미 최고 학력이에요!"
	end
	local gain = math.random(2, 4)
	s.Education = clamp(s.Education + gain)
	s.Happiness = clamp(s.Happiness - 1)
	return true, ("📚 열심히 공부했다! 학력 +%d"):format(gain)
end

function Actions.Work(_, s)
	local job = s.Job and Config.getJob(s.Job)
	if not job then
		return false, "직업이 없어요. 📋 직업 센터에서 일자리를 찾아보세요!"
	end
	s.Money += job.salary
	s.Happiness = clamp(s.Happiness - 1)
	return true, ("💼 %s 일을 해서 %s을 벌었다!"):format(job.name, Config.formatMoney(job.salary))
end

function Actions.Hospital(_, s)
	if s.Health >= 100 then
		return false, "이미 아주 건강해요!"
	end
	local cost = s.Age < 20 and 0 or Config.HOSPITAL_COST
	if s.Money < cost then
		return false, ("치료비 %s이 부족해요."):format(Config.formatMoney(cost))
	end
	s.Money -= cost
	s.Health = clamp(s.Health + 35)
	if cost == 0 then
		return true, "🏥 치료를 받았다! 건강 +35 (어린이는 무료)"
	end
	return true, ("🏥 치료를 받았다! 건강 +35 (-%s)"):format(Config.formatMoney(cost))
end

function Actions.Park(_, s)
	local gain = s.Age < 6 and 10 or 6
	s.Happiness = clamp(s.Happiness + gain)
	s.Health = clamp(s.Health + 2)
	return true, ("🌳 공원에서 쉬었다. 행복 +%d"):format(gain)
end

function Actions.ChooseJob(_, s, jobId)
	local job = type(jobId) == "string" and Config.getJob(jobId)
	if not job then
		return false, "없는 직업이에요."
	end
	if s.Job == job.id then
		return false, "이미 이 일을 하고 있어요."
	end
	if s.Age < job.minAge then
		return false, ("%d살 이상만 지원할 수 있어요."):format(job.minAge)
	end
	if s.Education < job.education then
		return false, ("학력이 %d 이상 필요해요. 학교에서 공부하세요!"):format(job.education)
	end
	s.Job = job.id
	return true, ("🎉 %s(으)로 취직했어요! 🏢 회사에 가서 일해보세요."):format(job.name)
end

function Actions.QuitJob(_, s)
	if not s.Job then
		return false, "직업이 없어요."
	end
	s.Job = nil
	return true, "회사를 그만뒀어요."
end

function Actions.Buy(_, s, itemId)
	local item = type(itemId) == "string" and Config.getItem(itemId)
	if not item then
		return false, "없는 물건이에요."
	end
	if s.Owned[item.id] then
		return false, "이미 가지고 있어요."
	end
	if s.Age < item.minAge then
		return false, ("%d살 이상만 살 수 있어요."):format(item.minAge)
	end
	if s.Money < item.price then
		return false, ("돈이 %s 부족해요."):format(Config.formatMoney(item.price - s.Money))
	end
	s.Money -= item.price
	s.Owned[item.id] = true
	s.Happiness = clamp(s.Happiness + item.happiness)
	return true, ("🛍️ %s을(를) 샀어요! 행복 +%d"):format(item.name, item.happiness)
end

local function performAction(player, action, arg)
	local s = states[player]
	if not s then
		return false, "데이터를 불러오는 중이에요."
	end
	if action == "Rebirth" then
		return rebirth(player)
	end
	if not s.Alive then
		return false, "이번 생은 끝났어요. 환생해 주세요."
	end
	local handler = Actions[action]
	if not handler then
		return false, "알 수 없는 행동이에요."
	end

	local cd = Config.Cooldowns[action]
	if cd then
		cooldowns[player] = cooldowns[player] or {}
		local left = cd - (os.clock() - (cooldowns[player][action] or 0))
		if left > 0 then
			return false, ("잠시 후에 다시 해 주세요. (%d초)"):format(math.ceil(left))
		end
	end

	local ok, msg = handler(player, s, arg)
	if ok and cd then
		cooldowns[player][action] = os.clock()
	end
	sync(player)
	return ok, msg
end

---------------------------------------------------------------------
-- 1년이 지날 때
---------------------------------------------------------------------
local function yearTick(player, s)
	local oldStage = Config.getStage(s.Age)
	s.Age += 1
	local newStage = Config.getStage(s.Age)

	if newStage ~= oldStage then
		notify(player, ("🎂 %d살! 이제 %s이에요!"):format(s.Age, newStage.name), "good")
		applyBody(player)
	else
		notify(player, ("🎂 %d살이 되었어요!"):format(s.Age), "info")
	end

	if s.Age >= 6 and s.Age < 20 then
		s.Money += Config.ALLOWANCE
	end

	if s.Age >= 50 then
		s.Health = clamp(s.Health - math.random(0, s.Age >= 70 and 5 or 2))
	end

	s.Happiness = clamp(s.Happiness + math.floor(itemBonus(s) / 10) - 2)
	if s.Happiness < 20 then
		s.Health = clamp(s.Health - 3)
		notify(player, "😢 너무 우울해서 건강이 나빠지고 있어요. 공원에서 쉬거나 물건을 사보세요.", "bad")
	end

	if math.random() < Config.EVENT_CHANCE then
		randomEvent(player, s)
	end

	if s.Health <= 0 then
		die(player, "건강 악화")
	elseif s.Age >= s.MaxAge then
		die(player, "노환")
	end
end

---------------------------------------------------------------------
-- 플레이어 입장 / 퇴장
---------------------------------------------------------------------
local function onPlayerAdded(player)
	local leaderstats = Instance.new("Folder")
	leaderstats.Name = "leaderstats"
	local age = Instance.new("IntValue")
	age.Name = "나이"
	age.Parent = leaderstats
	local money = Instance.new("IntValue")
	money.Name = "돈"
	money.Parent = leaderstats
	leaderstats.Parent = player

	local s = newLife()
	local saved = DataManager.load(player.UserId)
	if type(saved) == "table" then
		for k, v in pairs(saved) do
			s[k] = v
		end
		if type(s.Owned) ~= "table" then
			s.Owned = {}
		end
	end
	if not player.Parent then
		return
	end
	states[player] = s
	cooldowns[player] = {}

	player.CharacterAdded:Connect(function(character)
		character:WaitForChild("HumanoidRootPart")
		task.wait(0.1)
		applyBody(player)
	end)
	if player.Character then
		applyBody(player)
	end
	sync(player)
end

local function onPlayerRemoving(player)
	local s = states[player]
	if s then
		DataManager.save(player.UserId, s)
	end
	states[player] = nil
	cooldowns[player] = nil
end

---------------------------------------------------------------------
-- 공개 함수
---------------------------------------------------------------------
function LifeService.handlePrompt(player, action)
	if action == "OpenShop" then
		remotes.OpenPanel:FireClient(player, "Shop")
	elseif action == "OpenJobs" then
		remotes.OpenPanel:FireClient(player, "Jobs")
	else
		local ok, msg = performAction(player, action)
		notify(player, msg, ok and "good" or "bad")
	end
end

function LifeService.init(remoteFolder)
	remotes = {
		Action = remoteFolder.Action,
		State = remoteFolder.State,
		Notify = remoteFolder.Notify,
		OpenPanel = remoteFolder.OpenPanel,
	}

	remotes.Action.OnServerInvoke = function(player, action, arg)
		if type(action) ~= "string" then
			return false, "잘못된 요청"
		end
		return performAction(player, action, arg)
	end

	Players.PlayerAdded:Connect(onPlayerAdded)
	Players.PlayerRemoving:Connect(onPlayerRemoving)
	for _, player in ipairs(Players:GetPlayers()) do
		task.spawn(onPlayerAdded, player)
	end

	-- 시간 흐름 (1초마다)
	task.spawn(function()
		while true do
			task.wait(1)
			for player, s in pairs(states) do
				if s.Alive and player.Parent then
					s.YearProgress += 1
					if s.YearProgress >= Config.SECONDS_PER_YEAR then
						s.YearProgress = 0
						local ok, err = pcall(yearTick, player, s)
						if not ok then
							warn("[LifeGame] yearTick 오류:", err)
						end
					end
					sync(player)
				end
			end
		end
	end)

	-- 자동 저장 (2분마다)
	task.spawn(function()
		while true do
			task.wait(120)
			for player, s in pairs(states) do
				DataManager.save(player.UserId, s)
			end
		end
	end)

	game:BindToClose(function()
		for player, s in pairs(states) do
			DataManager.save(player.UserId, s)
		end
	end)
end

return LifeService
