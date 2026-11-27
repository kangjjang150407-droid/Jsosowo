-- 플레이어 인생 데이터 저장/불러오기
local DataStoreService = game:GetService("DataStoreService")

local store
local ok = pcall(function()
	store = DataStoreService:GetDataStore("LifeGame_v1")
end)
if not ok then
	warn("[LifeGame] DataStore를 쓸 수 없어요. 저장 없이 진행합니다. (Studio에서는 게임 설정 > 보안 > API 서비스 허용)")
end

local DataManager = {}

function DataManager.load(userId)
	if not store then
		return nil
	end
	local success, data = pcall(function()
		return store:GetAsync("player_" .. userId)
	end)
	if success then
		return data
	end
	warn("[LifeGame] 데이터 불러오기 실패:", data)
	return nil
end

function DataManager.save(userId, data)
	if not store then
		return
	end
	local success, err = pcall(function()
		store:SetAsync("player_" .. userId, data)
	end)
	if not success then
		warn("[LifeGame] 데이터 저장 실패:", err)
	end
end

return DataManager
