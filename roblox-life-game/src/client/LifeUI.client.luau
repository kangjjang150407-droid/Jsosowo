-- 인생 게임 화면 UI: 상태창, 알림, 직업/상점 창, 사망 화면
local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local TweenService = game:GetService("TweenService")

local player = Players.LocalPlayer
local Config = require(ReplicatedStorage:WaitForChild("Shared"):WaitForChild("Config"))
local Remotes = ReplicatedStorage:WaitForChild("LifeRemotes")
local ActionRF = Remotes:WaitForChild("Action")
local StateRE = Remotes:WaitForChild("State")
local NotifyRE = Remotes:WaitForChild("Notify")
local OpenPanelRE = Remotes:WaitForChild("OpenPanel")

local WHITE = Color3.new(1, 1, 1)
local SUBTEXT = Color3.fromRGB(185, 185, 200)
local PANEL_BG = Color3.fromRGB(30, 30, 42)
local ROW_BG = Color3.fromRGB(45, 45, 60)
local DISABLED = Color3.fromRGB(80, 80, 90)

local state = nil

---------------------------------------------------------------------
-- UI 도우미
---------------------------------------------------------------------
local function make(className, props, children)
	local inst = Instance.new(className)
	for key, value in pairs(props or {}) do
		if key ~= "Parent" then
			inst[key] = value
		end
	end
	for _, child in ipairs(children or {}) do
		child.Parent = inst
	end
	if props and props.Parent then
		inst.Parent = props.Parent
	end
	return inst
end

local function corner(radius)
	return make("UICorner", { CornerRadius = UDim.new(0, radius or 8) })
end

local function padding(px)
	local u = UDim.new(0, px)
	return make("UIPadding", { PaddingTop = u, PaddingBottom = u, PaddingLeft = u, PaddingRight = u })
end

local function textLabel(parent, text, textSize, order, color, font)
	return make("TextLabel", {
		Size = UDim2.new(1, 0, 0, textSize + 6),
		BackgroundTransparency = 1,
		Text = text,
		TextColor3 = color or WHITE,
		Font = font or Enum.Font.GothamBold,
		TextSize = textSize,
		TextXAlignment = Enum.TextXAlignment.Left,
		LayoutOrder = order,
		Parent = parent,
	})
end

local function progressBar(parent, name, color, order)
	local holder = make("Frame", {
		Size = UDim2.new(1, 0, 0, 32),
		BackgroundTransparency = 1,
		LayoutOrder = order,
		Parent = parent,
	})
	local title = make("TextLabel", {
		Size = UDim2.new(1, 0, 0, 14),
		BackgroundTransparency = 1,
		Text = name,
		TextColor3 = SUBTEXT,
		Font = Enum.Font.GothamMedium,
		TextSize = 12,
		TextXAlignment = Enum.TextXAlignment.Left,
		Parent = holder,
	})
	local back = make("Frame", {
		Size = UDim2.new(1, 0, 0, 12),
		Position = UDim2.fromOffset(0, 17),
		BackgroundColor3 = Color3.fromRGB(60, 60, 75),
		Parent = holder,
	}, { corner(6) })
	local fill = make("Frame", {
		Size = UDim2.fromScale(0, 1),
		BackgroundColor3 = color,
		Parent = back,
	}, { corner(6) })

	return function(value, max, text)
		local ratio = math.clamp(value / max, 0, 1)
		TweenService:Create(fill, TweenInfo.new(0.25), { Size = UDim2.fromScale(ratio, 1) }):Play()
		title.Text = name .. "   " .. (text or ("%d / %d"):format(math.floor(value), max))
	end
end

local gui = make("ScreenGui", {
	Name = "LifeUI",
	ResetOnSpawn = false,
	ZIndexBehavior = Enum.ZIndexBehavior.Sibling,
	Parent = player:WaitForChild("PlayerGui"),
})

---------------------------------------------------------------------
-- 상태창 (왼쪽 위)
---------------------------------------------------------------------
local statFrame = make("Frame", {
	Name = "Stats",
	Size = UDim2.fromOffset(270, 0),
	AutomaticSize = Enum.AutomaticSize.Y,
	Position = UDim2.fromOffset(12, 12),
	BackgroundColor3 = Color3.fromRGB(25, 25, 35),
	BackgroundTransparency = 0.15,
	Parent = gui,
}, {
	corner(12),
	padding(12),
	make("UIListLayout", { Padding = UDim.new(0, 4), SortOrder = Enum.SortOrder.LayoutOrder }),
})

local titleLabel = textLabel(statFrame, "인생 불러오는 중...", 15, 1, Color3.fromRGB(255, 220, 120))
local ageLabel = textLabel(statFrame, "", 18, 2)
local moneyLabel = textLabel(statFrame, "", 18, 3, Color3.fromRGB(140, 230, 140))
local jobLabel = textLabel(statFrame, "", 15, 4)
local setHealth = progressBar(statFrame, "❤️ 건강", Color3.fromRGB(235, 80, 90), 5)
local setHappy = progressBar(statFrame, "😊 행복", Color3.fromRGB(250, 200, 70), 6)
local setEdu = progressBar(statFrame, "🎓 학력", Color3.fromRGB(90, 160, 250), 7)
local setYear = progressBar(statFrame, "⏳ 다음 생일까지", Color3.fromRGB(180, 130, 240), 8)
local ownedLabel = textLabel(statFrame, "", 13, 9, SUBTEXT, Enum.Font.GothamMedium)
ownedLabel.TextWrapped = true
ownedLabel.AutomaticSize = Enum.AutomaticSize.Y

make("TextLabel", {
	Size = UDim2.new(0, 420, 0, 28),
	AnchorPoint = Vector2.new(0.5, 1),
	Position = UDim2.new(0.5, 0, 1, -12),
	BackgroundColor3 = Color3.fromRGB(25, 25, 35),
	BackgroundTransparency = 0.3,
	Text = "건물 문 앞에서 [E] 키를 눌러 행동하세요!",
	TextColor3 = WHITE,
	Font = Enum.Font.GothamMedium,
	TextSize = 14,
	Parent = gui,
}, { corner(14) })

---------------------------------------------------------------------
-- 알림 (오른쪽 위)
---------------------------------------------------------------------
local toastHolder = make("Frame", {
	Size = UDim2.fromOffset(320, 400),
	Position = UDim2.new(1, -332, 0, 12),
	BackgroundTransparency = 1,
	Parent = gui,
}, {
	make("UIListLayout", { Padding = UDim.new(0, 6), SortOrder = Enum.SortOrder.LayoutOrder }),
})

local KIND_COLORS = {
	good = Color3.fromRGB(45, 140, 80),
	bad = Color3.fromRGB(170, 55, 60),
	info = Color3.fromRGB(50, 70, 120),
}
local toastCount = 0

local function toast(text, kind)
	if not text or text == "" then
		return
	end
	toastCount += 1
	local t = make("TextLabel", {
		Size = UDim2.new(1, 0, 0, 0),
		AutomaticSize = Enum.AutomaticSize.Y,
		BackgroundColor3 = KIND_COLORS[kind] or KIND_COLORS.info,
		BackgroundTransparency = 0.1,
		Text = text,
		TextWrapped = true,
		TextColor3 = WHITE,
		Font = Enum.Font.GothamMedium,
		TextSize = 15,
		TextXAlignment = Enum.TextXAlignment.Left,
		LayoutOrder = toastCount,
		Parent = toastHolder,
	}, { corner(8), padding(8) })

	local toasts = {}
	for _, child in ipairs(toastHolder:GetChildren()) do
		if child:IsA("TextLabel") then
			table.insert(toasts, child)
		end
	end
	if #toasts > 6 then
		table.sort(toasts, function(a, b)
			return a.LayoutOrder < b.LayoutOrder
		end)
		toasts[1]:Destroy()
	end

	task.delay(4.5, function()
		if t.Parent then
			TweenService:Create(t, TweenInfo.new(0.4), { BackgroundTransparency = 1, TextTransparency = 1 }):Play()
			task.wait(0.4)
			t:Destroy()
		end
	end)
end

---------------------------------------------------------------------
-- 직업 / 상점 창
---------------------------------------------------------------------
local panels = {}

local function makePanel(titleText)
	local frame = make("Frame", {
		Size = UDim2.fromOffset(460, 460),
		AnchorPoint = Vector2.new(0.5, 0.5),
		Position = UDim2.fromScale(0.5, 0.5),
		BackgroundColor3 = PANEL_BG,
		Visible = false,
		ZIndex = 5,
		Parent = gui,
	}, { corner(14), make("UISizeConstraint", { MaxSize = Vector2.new(460, 460) }) })

	make("TextLabel", {
		Size = UDim2.new(1, -70, 0, 40),
		Position = UDim2.fromOffset(16, 8),
		BackgroundTransparency = 1,
		Text = titleText,
		TextColor3 = WHITE,
		Font = Enum.Font.GothamBold,
		TextSize = 22,
		TextXAlignment = Enum.TextXAlignment.Left,
		Parent = frame,
	})

	local close = make("TextButton", {
		Size = UDim2.fromOffset(36, 36),
		Position = UDim2.new(1, -46, 0, 10),
		BackgroundColor3 = Color3.fromRGB(200, 70, 70),
		Text = "✕",
		TextColor3 = WHITE,
		Font = Enum.Font.GothamBold,
		TextSize = 18,
		Parent = frame,
	}, { corner(8) })
	close.MouseButton1Click:Connect(function()
		frame.Visible = false
	end)

	local list = make("ScrollingFrame", {
		Size = UDim2.new(1, -24, 1, -112),
		Position = UDim2.fromOffset(12, 56),
		BackgroundTransparency = 1,
		BorderSizePixel = 0,
		CanvasSize = UDim2.new(),
		AutomaticCanvasSize = Enum.AutomaticSize.Y,
		ScrollBarThickness = 6,
		Parent = frame,
	}, { make("UIListLayout", { Padding = UDim.new(0, 6), SortOrder = Enum.SortOrder.LayoutOrder }) })

	local footer = make("Frame", {
		Size = UDim2.new(1, -24, 0, 44),
		Position = UDim2.new(0, 12, 1, -52),
		BackgroundTransparency = 1,
		Parent = frame,
	})

	table.insert(panels, frame)
	return frame, list, footer
end

local function clearList(list)
	for _, child in ipairs(list:GetChildren()) do
		if not child:IsA("UIListLayout") then
			child:Destroy()
		end
	end
end

local function row(list, order, titleText, subText, buttonText, buttonColor, enabled, onClick)
	local r = make("Frame", {
		Size = UDim2.new(1, -8, 0, 60),
		BackgroundColor3 = ROW_BG,
		LayoutOrder = order,
		Parent = list,
	}, { corner(10) })
	make("TextLabel", {
		Size = UDim2.new(1, -124, 0, 26),
		Position = UDim2.fromOffset(12, 6),
		BackgroundTransparency = 1,
		Text = titleText,
		TextColor3 = WHITE,
		Font = Enum.Font.GothamBold,
		TextSize = 17,
		TextXAlignment = Enum.TextXAlignment.Left,
		Parent = r,
	})
	make("TextLabel", {
		Size = UDim2.new(1, -124, 0, 20),
		Position = UDim2.fromOffset(12, 32),
		BackgroundTransparency = 1,
		Text = subText,
		TextColor3 = SUBTEXT,
		Font = Enum.Font.Gotham,
		TextSize = 13,
		TextXAlignment = Enum.TextXAlignment.Left,
		TextTruncate = Enum.TextTruncate.AtEnd,
		Parent = r,
	})
	local button = make("TextButton", {
		Size = UDim2.fromOffset(100, 38),
		Position = UDim2.new(1, -110, 0.5, -19),
		BackgroundColor3 = enabled and buttonColor or DISABLED,
		AutoButtonColor = enabled,
		Text = buttonText,
		TextColor3 = WHITE,
		Font = Enum.Font.GothamBold,
		TextSize = 15,
		Parent = r,
	}, { corner(8) })
	if enabled then
		button.MouseButton1Click:Connect(onClick)
	end
end

local function callAction(action, arg)
	local ok, success, msg = pcall(ActionRF.InvokeServer, ActionRF, action, arg)
	if not ok then
		toast("서버와 통신하지 못했어요.", "bad")
		return
	end
	toast(msg, success and "good" or "bad")
end

-- 직업 창
local jobPanel, jobList, jobFooter = makePanel("📋 직업 센터")
local quitButton = make("TextButton", {
	Size = UDim2.new(1, 0, 1, 0),
	BackgroundColor3 = Color3.fromRGB(190, 90, 60),
	Text = "회사 그만두기",
	TextColor3 = WHITE,
	Font = Enum.Font.GothamBold,
	TextSize = 16,
	Parent = jobFooter,
}, { corner(10) })
quitButton.MouseButton1Click:Connect(function()
	callAction("QuitJob")
end)

local function refreshJobs()
	clearList(jobList)
	if not state then
		return
	end
	for i, job in ipairs(Config.Jobs) do
		local sub = ("1회 급여 %s · %d살 이상 · 학력 %d"):format(Config.formatMoney(job.salary), job.minAge, job.education)
		local text, enabled = "지원하기", true
		if state.Job == job.id then
			text, enabled = "재직 중", false
		elseif state.Age < job.minAge then
			text, enabled = "나이 부족", false
		elseif state.Education < job.education then
			text, enabled = "학력 부족", false
		end
		row(jobList, i, job.name, sub, text, Color3.fromRGB(70, 150, 240), enabled, function()
			callAction("ChooseJob", job.id)
		end)
	end
	quitButton.Visible = state.Job ~= nil
end

-- 상점 창
local shopPanel, shopList, shopFooter = makePanel("🛒 쇼핑몰")
local shopMoneyLabel = make("TextLabel", {
	Size = UDim2.fromScale(1, 1),
	BackgroundTransparency = 1,
	TextColor3 = Color3.fromRGB(140, 230, 140),
	Font = Enum.Font.GothamBold,
	TextSize = 18,
	Parent = shopFooter,
})

local function refreshShop()
	clearList(shopList)
	if not state then
		return
	end
	shopMoneyLabel.Text = "내 돈: " .. Config.formatMoney(state.Money)
	for i, item in ipairs(Config.Items) do
		local sub = ("%s · %s · 행복 +%d · %d살 이상"):format(
			item.category, Config.formatMoney(item.price), item.happiness, item.minAge)
		local text, enabled = "구매", true
		if state.Owned[item.id] then
			text, enabled = "보유 중", false
		elseif state.Age < item.minAge then
			text, enabled = "나이 부족", false
		elseif state.Money < item.price then
			text, enabled = "돈 부족", false
		end
		row(shopList, i, item.name, sub, text, Color3.fromRGB(60, 170, 100), enabled, function()
			callAction("Buy", item.id)
		end)
	end
end

local function openPanel(which)
	for _, panel in ipairs(panels) do
		panel.Visible = false
	end
	if which == "Jobs" then
		refreshJobs()
		jobPanel.Visible = true
	elseif which == "Shop" then
		refreshShop()
		shopPanel.Visible = true
	end
end

---------------------------------------------------------------------
-- 사망 화면
---------------------------------------------------------------------
local deathFrame = make("Frame", {
	Size = UDim2.fromScale(1, 1),
	BackgroundColor3 = Color3.new(0, 0, 0),
	BackgroundTransparency = 0.35,
	Visible = false,
	ZIndex = 10,
	Parent = gui,
})
local deathCard = make("Frame", {
	Size = UDim2.fromOffset(420, 320),
	AnchorPoint = Vector2.new(0.5, 0.5),
	Position = UDim2.fromScale(0.5, 0.5),
	BackgroundColor3 = PANEL_BG,
	Parent = deathFrame,
}, { corner(16), padding(20) })
make("TextLabel", {
	Size = UDim2.new(1, 0, 0, 40),
	BackgroundTransparency = 1,
	Text = "🕊️ 인생 종료",
	TextColor3 = WHITE,
	Font = Enum.Font.GothamBold,
	TextSize = 28,
	Parent = deathCard,
})
local deathText = make("TextLabel", {
	Size = UDim2.new(1, 0, 1, -110),
	Position = UDim2.fromOffset(0, 48),
	BackgroundTransparency = 1,
	Text = "",
	TextColor3 = SUBTEXT,
	Font = Enum.Font.GothamMedium,
	TextSize = 16,
	TextWrapped = true,
	TextYAlignment = Enum.TextYAlignment.Top,
	Parent = deathCard,
})
local rebirthButton = make("TextButton", {
	Size = UDim2.new(1, 0, 0, 50),
	Position = UDim2.new(0, 0, 1, -50),
	BackgroundColor3 = Color3.fromRGB(250, 190, 60),
	Text = "👶 환생하기",
	TextColor3 = Color3.fromRGB(40, 30, 10),
	Font = Enum.Font.GothamBold,
	TextSize = 20,
	Parent = deathCard,
}, { corner(10) })
rebirthButton.MouseButton1Click:Connect(function()
	callAction("Rebirth")
end)

---------------------------------------------------------------------
-- 서버 이벤트
---------------------------------------------------------------------
local lastSignature = ""

StateRE.OnClientEvent:Connect(function(newState)
	state = newState
	local stage = Config.getStage(state.Age)
	local job = state.Job and Config.getJob(state.Job)

	titleLabel.Text = ("%s님의 %d번째 인생"):format(player.DisplayName, state.Lives)
	ageLabel.Text = ("🎂 %d살 (%s)"):format(state.Age, stage.name)
	moneyLabel.Text = "💰 " .. Config.formatMoney(state.Money)
	jobLabel.Text = "💼 " .. (job and job.name or "무직")
	setHealth(state.Health, 100)
	setHappy(state.Happiness, 100)
	setEdu(state.Education, 100)
	setYear(state.YearProgress, Config.SECONDS_PER_YEAR, ("%d초"):format(Config.SECONDS_PER_YEAR - state.YearProgress))

	local ownedNames, ownedCount = {}, 0
	for _, item in ipairs(Config.Items) do
		if state.Owned[item.id] then
			table.insert(ownedNames, item.name)
			ownedCount += 1
		end
	end
	ownedLabel.Text = "🎒 " .. (ownedCount > 0 and table.concat(ownedNames, ", ") or "가진 물건 없음")

	deathFrame.Visible = not state.Alive
	if not state.Alive then
		for _, panel in ipairs(panels) do
			panel.Visible = false
		end
		deathText.Text = ("%d살에 %s(으)로 세상을 떠났어요.\n\n직업: %s\n남긴 돈: %s\n인생 점수: %d점\n최고 점수: %d점\n\n환생하면 점수의 %d%%를 유산으로 받아요."):format(
			state.Age,
			state.DeathReason or "알 수 없는 이유",
			job and job.name or "무직",
			Config.formatMoney(state.Money),
			state.FinalScore or 0,
			state.BestScore or 0,
			math.floor(Config.INHERITANCE_RATE * 100)
		)
	end

	local signature = table.concat({
		tostring(state.Job), state.Age, state.Education, state.Money, ownedCount, tostring(state.Alive),
	}, "|")
	if signature ~= lastSignature then
		lastSignature = signature
		if jobPanel.Visible then
			refreshJobs()
		end
		if shopPanel.Visible then
			refreshShop()
		end
	end
end)

NotifyRE.OnClientEvent:Connect(toast)
OpenPanelRE.OnClientEvent:Connect(openPanel)
