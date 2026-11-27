-- 서버 시작점: 리모트 생성 → 인생 시스템 시작 → 마을 생성
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local folder = Instance.new("Folder")
folder.Name = "LifeRemotes"

local function addRemote(className, name)
	local remote = Instance.new(className)
	remote.Name = name
	remote.Parent = folder
end

addRemote("RemoteFunction", "Action") -- 클라이언트 → 서버 행동 요청
addRemote("RemoteEvent", "State") -- 서버 → 클라이언트 상태 전송
addRemote("RemoteEvent", "Notify") -- 서버 → 클라이언트 알림
addRemote("RemoteEvent", "OpenPanel") -- 서버 → 클라이언트 창 열기
folder.Parent = ReplicatedStorage

local LifeService = require(script.Parent.LifeService)
local WorldBuilder = require(script.Parent.WorldBuilder)

LifeService.init(folder)
WorldBuilder.build(LifeService.handlePrompt)
