-- 마을(땅, 광장, 건물, 길, 나무)을 코드로 자동 생성
local Workspace = game:GetService("Workspace")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Config = require(ReplicatedStorage:WaitForChild("Shared"):WaitForChild("Config"))

local WorldBuilder = {}

local function part(props)
	local p = Instance.new("Part")
	p.Anchored = true
	p.TopSurface = Enum.SurfaceType.Smooth
	p.BottomSurface = Enum.SurfaceType.Smooth
	for key, value in pairs(props) do
		if key ~= "Parent" then
			p[key] = value
		end
	end
	p.Parent = props.Parent
	return p
end

local function sign(adornee, text, offsetY)
	local billboard = Instance.new("BillboardGui")
	billboard.Size = UDim2.fromOffset(240, 60)
	billboard.StudsOffset = Vector3.new(0, offsetY, 0)
	billboard.MaxDistance = 250
	billboard.LightInfluence = 0
	billboard.Adornee = adornee

	local label = Instance.new("TextLabel")
	label.Size = UDim2.fromScale(1, 1)
	label.BackgroundColor3 = Color3.fromRGB(25, 25, 35)
	label.BackgroundTransparency = 0.2
	label.TextColor3 = Color3.new(1, 1, 1)
	label.Font = Enum.Font.GothamBold
	label.TextScaled = true
	label.Text = text
	label.Parent = billboard

	local padding = Instance.new("UIPadding")
	padding.PaddingTop = UDim.new(0, 6)
	padding.PaddingBottom = UDim.new(0, 6)
	padding.Parent = label

	local uiCorner = Instance.new("UICorner")
	uiCorner.CornerRadius = UDim.new(0, 12)
	uiCorner.Parent = label

	billboard.Parent = adornee
end

local function tree(parent, position)
	local height = math.random(8, 14)
	part({
		Name = "Trunk",
		Size = Vector3.new(2, height, 2),
		Position = position + Vector3.new(0, height / 2, 0),
		Color = Color3.fromRGB(110, 75, 45),
		Material = Enum.Material.Wood,
		Parent = parent,
	})
	local leafSize = math.random(8, 12)
	part({
		Name = "Leaves",
		Shape = Enum.PartType.Ball,
		Size = Vector3.new(leafSize, leafSize, leafSize),
		Position = position + Vector3.new(0, height + leafSize / 3, 0),
		Color = Color3.fromRGB(60, 150 + math.random(0, 40), 60),
		Material = Enum.Material.Grass,
		Parent = parent,
	})
end

function WorldBuilder.build(onPrompt)
	local world = Instance.new("Folder")
	world.Name = "LifeWorld"
	world.Parent = Workspace

	if not Workspace:FindFirstChild("Baseplate") then
		part({
			Name = "Ground",
			Size = Vector3.new(512, 2, 512),
			Position = Vector3.new(0, -1, 0),
			Color = Color3.fromRGB(106, 170, 80),
			Material = Enum.Material.Grass,
			Parent = world,
		})
	end

	if not Workspace:FindFirstChildWhichIsA("SpawnLocation", true) then
		local spawn = Instance.new("SpawnLocation")
		spawn.Anchored = true
		spawn.Size = Vector3.new(12, 1, 12)
		spawn.Position = Vector3.new(0, 0.5, 0)
		spawn.Duration = 0
		spawn.Color = Color3.fromRGB(255, 220, 120)
		spawn.Parent = world
	end

	-- 중앙 광장
	part({
		Name = "Plaza",
		Shape = Enum.PartType.Cylinder,
		Size = Vector3.new(0.4, 60, 60),
		CFrame = CFrame.new(0, 0.2, 0) * CFrame.Angles(0, 0, math.rad(90)),
		Color = Color3.fromRGB(200, 195, 185),
		Material = Enum.Material.Concrete,
		Parent = world,
	})

	-- 건물들
	local count = #Config.Places
	for i, place in ipairs(Config.Places) do
		local angle = (i - 1) / count * math.pi * 2
		local size = place.size
		local position = Vector3.new(
			math.cos(angle) * Config.WORLD_RADIUS,
			size.Y / 2,
			math.sin(angle) * Config.WORLD_RADIUS
		)
		-- 건물 정면(-Z)이 광장을 바라보도록
		local cf = CFrame.lookAt(position, Vector3.new(0, position.Y, 0))

		local model = Instance.new("Model")
		model.Name = place.id
		model.Parent = world

		local body = part({
			Name = "Body",
			Size = size,
			CFrame = cf,
			Color = place.color,
			Material = Enum.Material.SmoothPlastic,
			Parent = model,
		})
		model.PrimaryPart = body

		local roof = part({
			Name = "Roof",
			Size = Vector3.new(size.X + 2, 1.5, size.Z + 2),
			CFrame = cf * CFrame.new(0, size.Y / 2 + 0.75, 0),
			Color = Color3.fromRGB(70, 70, 85),
			Parent = model,
		})

		local door = part({
			Name = "Door",
			Size = Vector3.new(6, 9, 0.6),
			CFrame = cf * CFrame.new(0, -size.Y / 2 + 4.5, -size.Z / 2 - 0.3),
			Color = Color3.fromRGB(95, 65, 40),
			Material = Enum.Material.Wood,
			Parent = model,
		})

		for _, x in ipairs({ -size.X / 3, size.X / 3 }) do
			part({
				Name = "Window",
				Size = Vector3.new(5, 4, 0.4),
				CFrame = cf * CFrame.new(x, size.Y / 6, -size.Z / 2 - 0.2),
				Color = Color3.fromRGB(170, 220, 255),
				Material = Enum.Material.Glass,
				Transparency = 0.2,
				Parent = model,
			})
		end

		-- 문에서 광장까지 길
		local start = Vector3.new(door.Position.X, 0.1, door.Position.Z)
		local finish = Vector3.new(0, 0.1, 0)
		part({
			Name = "Path",
			Size = Vector3.new(8, 0.2, (finish - start).Magnitude),
			CFrame = CFrame.lookAt((start + finish) / 2, finish),
			Color = Color3.fromRGB(150, 150, 155),
			Material = Enum.Material.Concrete,
			Parent = world,
		})

		sign(roof, place.name, 5)

		local prompt = Instance.new("ProximityPrompt")
		prompt.ActionText = place.prompt
		prompt.ObjectText = place.name
		prompt.HoldDuration = 0
		prompt.MaxActivationDistance = 12
		prompt.RequiresLineOfSight = false
		prompt.Parent = door
		prompt.Triggered:Connect(function(player)
			onPrompt(player, place.action)
		end)
	end

	-- 바깥쪽 나무
	local trees = Instance.new("Folder")
	trees.Name = "Trees"
	trees.Parent = world
	for _ = 1, 40 do
		local angle = math.random() * math.pi * 2
		local radius = math.random(115, 220)
		tree(trees, Vector3.new(math.cos(angle) * radius, 0, math.sin(angle) * radius))
	end
end

return WorldBuilder
