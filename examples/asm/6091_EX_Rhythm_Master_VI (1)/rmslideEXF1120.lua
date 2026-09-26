local BpmList = {}
local cur = 0;
local rawImd;
local notecount = 0;
local audioTime;
local TrackWidth = 64
local KNum = 4
local FlickWidth = 31
local NoteW = 406
local DiffName = "hd"
local xv={}
local notescale={}
local xvhit={}
local notescalehit={}
local function loadchart()
    -- 这玩意会在Init和Onretry都被执行。
    -- 诸如谱面信息之类的，在重新开始时应该/必须重新生成的信息，应该在这里放。
    -- 不要提前存储谱面信息，应当允许热重载。

    -- 初始化文件
    local charttitle=Chart:ChartInfo("Title")
    local version=string.lower(Chart:ChartInfo("version"))
    local ImdDiff={"hd","nm","mx","ez","sp"}
    local underlinepos,_=string.find(charttitle,"_")
    if (underlinepos~=nil) then
        charttitle=string.sub(charttitle,1,underlinepos-1)
    end
    Diff(version)
    filename=charttitle.."_"..KNum.."k_"..DiffName
    rawImd=Game:ReadBytes(filename..".imd")
    rawRmp=Game:ReadFile(filename..".rmp")
    rawJson=Game:ReadFile(filename..".json")
    if not (rawImd) then
        for i=4,6 do
            for k=1,5 do
                filename=charttitle.."_"..i.."k_"..ImdDiff[k]..".imd"
                rawImd=Game:ReadBytes(filename)
                if (rawImd) then
                    KNum=i
                    break
                end
            end
        end
    end
    TrackWidth=255//KNum
    FlickWidth=(TrackWidth-1)//2
    if rawImd then
        cur = ImdBPM(rawImd) + 6;-- 读完bpm返回指针，并跳过两个03和物量？
        audioTime = B2L(Bytes2Int,rawImd[0],rawImd[1],rawImd[2],rawImd[3]);
        -- 剩下的Note逻辑到OPN读，不要提前运算
    elseif rawJson then
        JsonFind(rawJson)
    else
        local bpmCount = Game:BpmCount();
        for i=1, bpmCount do
            BpmList[i]=Game:BpmAt(i-1)
        end
        audioTime = Game:AudioLength();
        KNum=0
        notecount=0
        mcnotecount=Chart:NoteCount()
        for i=1,mcnotecount do
            local mcnote=Chart:NoteAt(i-1)
            if mcnote.type==1 or mcnote.type==5 or mcnote.type==7 then
                if mcnote.width>=57 then
                    KNum=4
                elseif mcnote.width>=47 and mcnote.width<57 then
                    KNum=5
                else
                    KNum=6
                end
                break
            end
        end
    end
    if (KNum==5) then
        NoteW=337.8
    elseif (KNum==6) then
        NoteW=290
    end
end
local function reco()
    stvalue=0
    uifeverbutton.Alpha=0
    uifeverv.Y=5000
    uifevertrack.Alpha=0
    uimaxcombo:SetColor(255,255,255)
    if (STbool=="Nope") then
        STstate=3
    else
        STstate=0
    end
    noteorder=30000
    notelist={{},{},{},{},{},{}}
    notecombolist={}
    notecur={1,1,1,1,1,1}
    notecurready={5000000,5000000,5000000,5000000,5000000,5000000}
    inrtable={3,3,3,3,3,3}
    datainrtable={3,3,3,3,3,3}
    databestcount=0
    datacoolcount=0
    datagoodcount=0
    datamisscount=0
    maxcombovalue=0
    dataacc=0
    datascorevalue=0
    bestcount=0
    coolcount=0
    goodcount=0
    misscount=0
	maxsource=0
    hpvalue=100
    datahpvalue=1000
    lastfingerid={0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}
    fingerid={0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}
    fingerban={0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}
    holdnote={}
    holdstate={0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}
	holdmap={0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}
	holdmapinverse={0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}
	itemholdstate={0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}

    if not itembool then
        item0=true
        uimaxcombo.Y=-200*sw1610
        itemhold=0
	    mg2p=0
	    m2g=0
	    g2p=0
	    dataitemhold=0
	    datamg2p=0
	    datam2g=0
	    datag2p=0
        uiItem_mg2p.Alpha=0
        uiItem_ln.Alpha=0
        uiItem_g2p.Alpha=0
        uiItem_m2g.Alpha=0
        uimg2pvalue.Alpha=0
        uilnvalue.Alpha=0
        uig2pvalue.Alpha=0
        uim2gvalue.Alpha=0
    else
        item0=false
        uimaxcombo.Y=-280*sw1610
        itemhold=20
	    mg2p=36
	    m2g=10
	    g2p=22
	    dataitemhold=20
	    datamg2p=36
	    datam2g=10
	    datag2p=22
        uiItem_mg2p.Alpha=100
        uiItem_ln.Alpha=100
        uiItem_m2g.Alpha=100
        uiItem_g2p.Alpha=100
        uimg2pvalue.Alpha=100
        uilnvalue.Alpha=100
        uig2pvalue.Alpha=100
        uim2gvalue.Alpha=100
    end
	uimg2pvalue.Text=mg2p
	uilnvalue.Text=itemhold
	uig2pvalue.Text=g2p
	uim2gvalue.Text=m2g
	uilnvalue.X=107*sw1610
	uim2gvalue.X=207*sw1610
	uig2pvalue.X=307*sw1610
	uimg2pvalue.X=407*sw1610
	uiItem_ln.X=87*sw1610
	uiItem_m2g.X=187*sw1610
	uiItem_g2p.X=287*sw1610
	uiItem_mg2p.X=387*sw1610
    holdstatecopy={0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}
    holdvalue={}
    flicknote={}
    flicktime={}
    flickstate={0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}
    noteok={{},{},{},{},{},{}}
    inr={{3,3,3,3,3,3,3},{3,3,3,3,3,3,3},{3,3,3,3,3,3,3},{3,3,3,3,3,3,3},{3,3,3,3,3,3,3},{3,3,3,3,3,3,3},{3,3,3,3,3,3,3},{3,3,3,3,3,3,3},{3,3,3,3,3,3,3},{3,3,3,3,3,3,3},{3,3,3,3,3,3,3},{3,3,3,3,3,3,3},{3,3,3,3,3,3,3},{3,3,3,3,3,3,3},{3,3,3,3,3,3,3},{3,3,3,3,3,3,3},{3,3,3,3,3,3,3},{3,3,3,3,3,3,3},{3,3,3,3,3,3,3},{3,3,3,3,3,3,3}}
    inrpressup={}
    hitnoteid={}
    flickstateid={}
    flicknoteid={}
    hitbreakid={}
    holdnoteid={}
    holdbreakid={}
    flickbreakid={}
    dtime={}
    seg={0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}
    holdcombovalue={1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1}
	lnnotecombo={0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}
	lnnotecount={0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}
    combovalue=0
    comboevent=0
    comboeventtime=5000000
    ljudgevalue=4
    judgevalue=4
    judgeevent=0
    judgeeventtime=5000000
    pressupevent={0,0,0,0,0,0}
    pressuptime={5000000,5000000,5000000,5000000,5000000,5000000}
    scorebonus=0
    scorevalue=0
	scorejudge="D"
    accvalue=0
    acccount=0
    uiscore.Text=0
    uiacc.Text="0.00"
    uicombo.Text=0
    uicombo.Alpha=0
    for i=1,5 do
        uijudge[i].Y=5000
    end
    for i=1,KNum do
        uikey[i].Alpha=0
        uipress[i].Alpha=0
    end
    for i=1,20 do
        uihitl[i].Alpha=0
    end
end
function Init()
    maxstvalue=10000
    Audiobool=Module:GetBool("开启按键音")
    if Audiobool then
        Tap_hitsound=Audio:Load("click.ogg")
        Drag_hitsound=Audio:Load("drag.ogg")
        Flick_hitsound=Audio:Load("flick.ogg")
    end
    Value_hitsound=Module:GetNumber("按键音量")
    if (Module:GetBool("自动ST")==true) then
        STbool="AutoST"
        STstate=0
    else
        if (Module:GetBool("开启ST V2")==true) then
            STbool="Open2ST"
            STstate=0
        else
            if (Module:GetBool("开启ST")==true) then
                STbool="OpensST"
                STstate=0
            else
                STbool="Nope"
                STstate=3
            end
        end
    end
    STstate2=0
    uimod=Module:Find("mod")
    itembool=Module:GetBool("开启道具")
	mirrorbool=string.find(uimod.Text,"Flip")
	if (string.find(uimod.Text,"Dash")~=nil) then
	    judgescale=1.2
	elseif (string.find(uimod.Text,"Rush")~=nil) then
	    judgescale=1.5
	elseif (string.find(uimod.Text,"Slow")~=nil) then
	    judgescale=0.8
	else
	    judgescale=1
	end
    loadchart()
    getjudge=Play:GetJudge()
    if (getjudge==1 or getjudge==11) then
        judgeoffset={sp=60*judgescale,p1=105*judgescale,p2=120*judgescale,p3=135*judgescale,g=200*judgescale}
    elseif (getjudge==2 or getjudge==12) then
        judgeoffset={sp=47*judgescale,p1=63*judgescale,p2=88*judgescale,p3=118*judgescale,g=200*judgescale}
    elseif (getjudge==3 or getjudge==13) then
        judgeoffset={sp=40*judgescale,p1=56*judgescale,p2=81*judgescale,p3=111*judgescale,g=200*judgescale}
    elseif (getjudge==4 or getjudge==14) then
        judgeoffset={sp=34*judgescale,p1=50*judgescale,p2=75*judgescale,p3=105*judgescale,g=200*judgescale}
    else
        judgeoffset={sp=25*judgescale,p1=50*judgescale,p2=75*judgescale,p3=105*judgescale,g=200*judgescale}
    end
    scenescale=Game:FieldMeta("Scale")
    trackangle=Game:FieldMeta("Angle")
    if (trackangle<30) then
        trackscale=1
    else
        trackscale=1-(trackangle-30)/200
    end
    width=Game:Width()
    if (width>=2192) then gscale=1.2*scenescale
    else gscale=width/2192*1.2*scenescale;end

    sw1610=math.min(1,width/1728);
	
	uiItem_mg2p=Module:Find("Item_AllPerfect")
    uiItem_mg2p.Scale=sw1610
    uiItem_mg2p.X=387*sw1610
    uiItem_mg2p.Y=-180*sw1610
    uiItem_ln=Module:Find("Item_FullLn")
    uiItem_ln.Scale=sw1610
    uiItem_ln.X=87*sw1610
    uiItem_ln.Y=-180*sw1610
    uiItem_m2g=Module:Find("Item_m2g")
    uiItem_m2g.Scale=sw1610
    uiItem_m2g.X=187*sw1610
    uiItem_m2g.Y=-180*sw1610
    uiItem_g2p=Module:Find("Item_g2p")
    uiItem_g2p.Scale=sw1610
    uiItem_g2p.X=287*sw1610
    uiItem_g2p.Y=-180*sw1610
    uimg2pvalue=Module:Find("APvalue")
    uimg2pvalue.Scale=sw1610
    uimg2pvalue.X=407*sw1610
    uimg2pvalue.Y=-200*sw1610
    uilnvalue=Module:Find("FLvalue")
    uilnvalue.Scale=sw1610
    uilnvalue.X=107*sw1610
    uilnvalue.Y=-200*sw1610
    uim2gvalue=Module:Find("MGvalue")
    uim2gvalue.Scale=sw1610
    uim2gvalue.X=207*sw1610
    uim2gvalue.Y=-200*sw1610
    uig2pvalue=Module:Find("GPvalue")
    uig2pvalue.Scale=sw1610
    uig2pvalue.X=307*sw1610
    uig2pvalue.Y=-200*sw1610
    uiItem_eff=Module:Find("Item_eff")
    uiItem_eff.Scale=sw1610
    uiItem_eff.Y=-180*sw1610
	
    uiscore=Module:Find("score")
    uimaxcombo=Module:Find("uimaxcombo")
    uiacc=Module:Find("acc")
    uihpbg=Module:Find("hpbg")
    uihp=Module:Find("hp")
    uiscorebg=Module:Find("scorebg")
    uipause=Module:Find("pause")
    uiprogress=Module:Find("progress")
    uiaccbg=Module:Find("accbg")
    uiacc=Module:Find("acc")
    uicombo=Module:Find("combo")
    uijudge={}
    for i=1,5 do uijudge[i]=Module:Find("judge"..i);end

    uitrackbg=Module:Find("trackbg")
    uitrackbg.Width=2325.4*gscale
    uitrackbg.Height=1200*trackscale

    uitrackline=Module:Find("trackline"..KNum)
    uitrackline.Width=uitrackline.Width*gscale
    uitrackline.Height=865*trackscale
    uitrackline.Alpha=100

    uikeybg=Module:Find("keybg"..KNum)
    uikeybg.Width=uikeybg.Width*gscale
    uikeybg.Height=133*trackscale
    uikeybg.Y=1080-1023*trackscale
    uikeybg.Alpha=100
    
    uihit={}
    uihitl={}
    uihitl[1]=Module:Find("hitl1")
    uihitl[1].Alpha=0
    uihitl[1].Y=1080-932*trackscale
    for i=2,20 do
        uihitl[i]=Module:Clone(uihitl[1],"hitl"..i)
        uihitl[i].Alpha=0
    end
    uikey={}
    uipress={}
    for i=1,KNum do
        uihit[i]=Module:Find("hit"..i)
        uikey[i]=Module:Find("key"..i..KNum)
        uipress[i]=Module:Find("press"..i..KNum)
        uikey[i].Alpha=0
        uipress[i].Alpha=0
        uihit[i].X=TrackTX(KNum,i)*gscale
        uihit[i].Y=1080-932*trackscale
        uikey[i].X=uikey[i].X*gscale
        uikey[i].Y=1080-1023*trackscale
        uikey[i].Width=uikey[i].Width*gscale
        uikey[i].Height=133*trackscale
        uipress[i].X=uipress[i].X*gscale
        uipress[i].Y=1080-867*trackscale
        uipress[i].Width=uipress[i].Width*gscale
        uipress[i].Height=uipress[i].Height*trackscale
    end
    uihpbg.Width=width
    uihpbg.Height=135*sw1610
    uihp.Width=498.1*sw1610
    uihp.Height=47.8*sw1610
    uihp.X=35*sw1610
    uihp.Y=-60*sw1610
    uiscorebg.Width=529.9*sw1610
    uiscorebg.Height=104*sw1610
    uiscorebg.X=width/2
    uiscorebg.Y=-29*sw1610
    uipause.Width=34*sw1610
    uipause.Height=41*sw1610
    uipause.X=width/2-50*sw1610
    uipause.Y=-80*sw1610
    uiscore.Scale=sw1610
    uiscore.X=width/2-120*sw1610
    uiscore.Y=-79*sw1610
    uiaccbg.Width=600*sw1610
    uiaccbg.Height=84*sw1610
    uiaccbg.X=width/2-380*sw1610
    uiaccbg.Y=-130*sw1610
    uiacc.Scale=sw1610
    uiacc.X=width/2-100*sw1610
    uiacc.Y=-172*sw1610
    uiacc.Text="0.00"
    uimaxcombo.Scale=sw1610
    uimaxcombo.X=-width/2+80*sw1610
    if itembool then
        uimaxcombo.Y=-280*sw1610
    else
        uimaxcombo.Y=-200*sw1610
    end
    uiprogress.Y=-10*sw1610
    uiprogress.Width=20
    if (width>=2120) then
        uiscorebg.X=1060
        uipause.X=1010
        uiscore.X=940
        uiaccbg.X=680
        uiacc.X=960
        
    end
    uitracklight={}
    for i=1,2 do
        uitracklight[i]=Module:Find("tracklight"..i)
        uitracklight[i].Width=787.47*gscale
        uitracklight[i].X=(-1162.7+2325.4*(i-1))*gscale
        uitracklight[i].Y=1080-1200*trackscale
        uitracklight[i].Height=874*trackscale
    end

    -- 初始化note元件
    group=Note:GetGroup("group")
    group.X=-width/2*gscale
    group.Y=540-932*trackscale
    group.ScaleX=gscale
    group.ScaleY=trackscale
    uistbar=group:AddGrid("uistbar","stbar.png")
    uistbar:SetVertex(0,0,384)
    uistbar:SetVertex(1,1000,384)
    uistbar:SetVertex(2,0,1000)
    uistbar:SetVertex(3,1000,1000)
    uistbarbg={}
    for i=1,3 do
        uistbarbg[i]=Module:Find("stbg"..i)
        uistbarbg[i].Width=343.5*gscale
        uistbarbg[i].Height=416*trackscale
        uistbarbg[i].X=-758.4*gscale
        uistbarbg[i].Y=-542*trackscale
    end
    uifeverv=Module:Find("feverv")
    uifeverv.Width=471*gscale
    uifeverv.Height=167*trackscale
    uifeverv.Y=5000
    uifeverbutton=Module:Find("feverbutton")
    feverbutton_X=uifeverbutton.X
    feverbutton_Y=uifeverbutton.Y
    feverbutton_R=uifeverbutton.Width*0.6
    uifevertrack=Module:Find("fevertrack")
    uifevertrack.Width=2105*gscale
    uifevertrack.Height=1026*trackscale
    
    -- Always: 先初始化元件，再初始化数据
    reco()
    autobool = Play:IsAuto()
    notex = KNum+1 -- 记得改，4k->多k


    if (autobool) then
        Play:SetMissTime(1)
    else
        Play:SetMissTime(200)
    end

    -- Score、Acc元件的Alpha值更迭
    BpmList[#BpmList+1]={time=5000000}
    for i=1,#BpmList-1 do
        uiscore:DoAlpha({start=BpmList[i].time,finish=BpmList[i].time+60000/BpmList[i].bpm,from=100,to=60,repeats=(BpmList[i+1].time-BpmList[i].time)/60000*BpmList[i].bpm//1+1})
        uiacc:DoAlpha({start=BpmList[i].time,finish=BpmList[i].time+60000/BpmList[i].bpm,from=100,to=60,repeats=(BpmList[i+1].time-BpmList[i].time)/60000*BpmList[i].bpm//1+1})
        for k=1,2 do
            uitracklight[k]:DoAlpha({start=BpmList[i].time,finish=BpmList[i].time+60000/BpmList[i].bpm,from=100,to=0,repeats=(BpmList[i+1].time-BpmList[i].time)/60000*BpmList[i].bpm//1+1})
		end
		uiprogress:DoWidth({start=0,finish=audioTime,from=20,to=width+20})
	end
end
function OnRetry()
    loadchart()
    reco()
    
end

function OnProcessNote()
    if rawImd then
        notecount = 0
        local i = 10000;
        while cur < rawImd.Count do
            i = i + 1;
			local line = rawImd[cur+6]
			if (mirrorbool~=nil) then
			    line = KNum-1-rawImd[cur+6]
			end
            
            local nType = rawImd[cur];-- width干啥的……我默认没用。
            if nType == 0 then
                local note = {
                    type = 1,
                    nid = i,
                    time = ListFun(Bytes2Int,rawImd,cur+2,cur+5),
                    x = line + 1,
                    extra = i-10000,
                    y = 10,
                };
                Note:AddVirtual(note)
                notecount=notecount+1
            elseif nType == 1 then
                local note = {
                    type = 5,
                    nid = i,
                    time = ListFun(Bytes2Int,rawImd,cur+2,cur+5),
                    x = line + 1,
                    extra = i-10000,
                    y = 10,
                }
				if (mirrorbool~=nil and rawImd[cur + 7]~=0) then
				    rawImd[cur + 10]=255-rawImd[cur + 10]
					rawImd[cur + 7]=256-rawImd[cur + 7]
				end
                if rawImd[cur + 10] == 255 then
                    note.arrow = rawImd[cur + 7] - 246;
                else
                    note.arrow = rawImd[cur + 7] + 10;
                end
                Note:AddVirtual(note);
                notecount=notecount+1
            elseif nType == 2 then
                local note = {
                    type = 2,
                    nid = i,
                    time = ListFun(Bytes2Int,rawImd,cur+2,cur+5),
                    x = line + 1,
                    extra = i-10000,
                    y = 10,
                }
                local segtime = ListFun(Bytes2Int,rawImd,cur+7,cur+10);
                note.segments = {
                    [1] = {x = line + 1,time = segtime},
                    Length = 1
                }
                note.endtime = note.time + segtime;
                Note:AddVirtual(note);
                dur=math.floor((segtime+2)*BpmList[1].bpm/1250)
                notecombolist[i-10000]=math.floor(dur/12+1)
                notecount=notecount+notecombolist[i-10000]
                 -- 这里以后要改，对齐mc谱面
            elseif nType == 0x61 or nType == 0x62 then
                local note = {
                    type = 7,
                    nid = i,
                    time = ListFun(Bytes2Int,rawImd,cur+2,cur+5),
                    x = line + 1,
                    y = 10,
                    extra = i-10000,
                    segments = {}
                }
                if nType == 0x61 then
                    notecombo = 0
                    spos = 1
                    stype = true
                    local snote = {
                        type = 10,
                        nid = i,
                        time = ListFun(Bytes2Int,rawImd,cur+2,cur+5),
                        x = line + 1,
                        y = 100 + spos,
                        extra = i - 10000,
                    }
					if (mirrorbool~=nil and rawImd[cur + 7]~=0) then
						rawImd[cur + 10]=255-rawImd[cur + 10]
						rawImd[cur + 7]=256-rawImd[cur + 7]
					end
					if rawImd[cur + 10] == 255 then
                        snote.arrow = rawImd[cur + 7] - 246;
                    else
                        snote.arrow = rawImd[cur + 7] + 10;
                    end
                    Note:AddVirtual(snote);         
                else
                    dur=math.floor((ListFun(Bytes2Int,rawImd,cur+7,cur+10)+2)*BpmList[1].bpm/1250)
                    notecombo = math.floor(dur/12+1)
                    spos = 0
                    stype = false
                end
                local segmentLength = 0;
				local isfirstseg=1
                while cur < rawImd.Count do
                    cur = cur + 11;
                    local nnType = rawImd[cur];
					local cline = rawImd[cur+6]
					
					if (mirrorbool~=nil) then
						cline = KNum-1-rawImd[cur+6]
					end
					
                    if nnType == 0x21 then
                        spos = spos + 1;
                        local snote = {
                            nid = i,
                            time = ListFun(Bytes2Int,rawImd,cur-9,cur-6),
                            endtime = ListFun(Bytes2Int,rawImd,cur+2,cur+5),
                            x = rawImd[cur-5]+1,
                            y = 100 + spos,
                            extra = i - 10000,
                        }
						if (mirrorbool~=nil) then
						    snote.x=KNum-1-rawImd[cur-5]+1
							snote.segments={[1]={x=KNum-1-rawImd[cur+6]+1}}
						else
						    snote.segments={[1]={x=rawImd[cur+6]+1}}
						end
                        
						if (mirrorbool~=nil and rawImd[cur + 7]~=0) then
							rawImd[cur + 10]=255-rawImd[cur + 10]
							rawImd[cur + 7]=256-rawImd[cur + 7]
						end
						if rawImd[cur + 10] == 255 then
                            snote.arrow = rawImd[cur + 7] - 246;
                        else
                            snote.arrow = rawImd[cur + 7] + 10;
                        end
                        if (spos==2 and stype) then
                            snote.type=11
                        else
                            snote.type=12
                        end
                        Note:AddVirtual(snote);
                    elseif nnType == 0x22 then
                        segmentLength = segmentLength + 1;
                        note.segments[segmentLength] = {
                            x = cline + 1,
                            time = ListFun(Bytes2Int,rawImd,cur+2,cur+5) - note.time,
                        }
                        dur=math.floor((ListFun(Bytes2Int,rawImd,cur+7,cur+10)+2)*BpmList[1].bpm/1250)
                        notecombo = notecombo + math.floor(dur/12+1)
                    elseif nnType == 0xa1 then--钩子尾
                        segmentLength = segmentLength + 1;
                        local segtime = ListFun(Bytes2Int,rawImd,cur+2,cur+5)
                        note.segments[segmentLength] = {
                            time = segtime - note.time,
                        };

						if (mirrorbool~=nil and rawImd[cur + 7]~=0) then
							rawImd[cur + 10]=255-rawImd[cur + 10]
							rawImd[cur + 7]=256-rawImd[cur + 7]
						end
						if rawImd[cur + 10] == 255 then -- 255左，0右
                            note.segments[segmentLength].x = cline - 255 + rawImd[cur + 7];
                        else
                            note.segments[segmentLength].x = cline + 1 + rawImd[cur + 7];
                        end

                        note.segments.Length = segmentLength;
                        note.endtime = segtime;
                        Note:AddVirtual(note);
                        notecombolist[i-10000]=notecombo+1
                        notecount=notecount+notecombo+1
                        spos = spos + 1
                        local snote = {
                            nid = i,
                            time = ListFun(Bytes2Int,rawImd,cur-9,cur-6),
                            endtime = ListFun(Bytes2Int,rawImd,cur+2,cur+5),
                            x = rawImd[cur-5]+1,
                            y = 100 + spos,
                            extra = i - 10000,
                        }
						if (mirrorbool~=nil) then
						    snote.x=KNum-1-rawImd[cur-5]+1
							snote.segments={[1]={x=KNum-1-rawImd[cur+6]+1}}
						else
						    snote.segments={[1]={x=rawImd[cur+6]+1}}
						end

                        if rawImd[cur + 10] == 255 then
                            snote.arrow = rawImd[cur + 7] - 246;
                        else
                            snote.arrow = rawImd[cur + 7] + 10;
                        end
                        if (spos==2 and stype) then
                            snote.type=21
                        else
                            snote.type=22
                        end
                        Note:AddVirtual(snote)
                        -- print('cur:',cur,'start:',note.time,'end:',segtime,'length:',segmentLength)
                        break;
                    elseif nnType == 0xa2 then--点尾
                        segmentLength = segmentLength + 1;
                        local segtime = ListFun(Bytes2Int,rawImd,cur+2,cur+5)
                        note.segments[segmentLength] = {
                            x = cline + 1,
                            time = segtime - note.time
                        };
                        dur=math.floor((ListFun(Bytes2Int,rawImd,cur+7,cur+10)+2)*BpmList[1].bpm/1250)
                        notecombo = notecombo + math.floor(dur/12+1)
                        segmentLength = segmentLength + 1;

                        segtime = segtime + ListFun(Bytes2Int,rawImd,cur+7,cur+10)
                        note.segments[segmentLength] = {
                            x = cline + 1,
                            time = segtime - note.time
                        };

                        note.segments.Length = segmentLength;
                        note.endtime = segtime;
                        Note:AddVirtual(note);
                        notecombolist[i-10000]=notecombo
                        notecount=notecount+notecombo
                        spos = spos + 1
                        local snote = {
                            nid = i,
                            time = ListFun(Bytes2Int,rawImd,cur+2,cur+5),
                            endtime = ListFun(Bytes2Int,rawImd,cur+2,cur+5) + ListFun(Bytes2Int,rawImd,cur+7,cur+10),
                            x = cline + 1,
                            y = 100 + spos,
                            extra = i - 10000,
                        }
                        if (spos==2 and stype) then
                            snote.type=31
                        else
                            snote.type=32
                        end
                        Note:AddVirtual(snote);
                        break;
                    else error(string.format('Unknown child type %x in nid %d at cursor %d !',nnType,i,cur));
                    end
                end
            else error('Unknown type "' .. tostring(nType) .. '" at cursor ' .. tostring(cur) .. '!')
            end
            cur = cur + 11;
        end
		uimaxcombo.Text="0/"..notecount.."\n0.00%(D)"
		if notecount<=20 then
		    maxscore=200*notecount
		elseif notecount>20 and notecount<=50 then
		    maxscore=332*notecount-2640
		elseif notecount>50 and notecount<=100 then
		    maxscore=466*notecount-9340
		else
		    maxscore=600*notecount-22740
		end
		maxstvalue=notecount*0.4
		stvalue=maxstvalue*math.min(Module:GetNumber("初始ST能量"),30)*0.01
    elseif rawJson then
        JsonNote()
        uimaxcombo.Text="0/"..notecount.."\n0.00%(D)"
		if notecount<=20 then
		    maxscore=200*notecount
		elseif notecount>20 and notecount<=50 then
		    maxscore=332*notecount-2640
		elseif notecount>50 and notecount<=100 then
		    maxscore=466*notecount-9340
		else
		    maxscore=600*notecount-22740
		end
		maxstvalue=notecount*0.4
		stvalue=maxstvalue*math.min(Module:GetNumber("初始ST能量"),30)*0.01
    else
        
        for i=1,mcnotecount do
            local mcnote=Chart:NoteAt(i-1)
            local mcnotex=mcnote.x
            mcnote.x=x2c(mcnotex)
            mcnote.y=10
            mcnote.extra=i
            if mcnote.type==1 then
                notecount=notecount+1
            elseif mcnote.type==5 then
                notecount=notecount+1
                if mcnote.arrow==1 then
                    mcnote.arrow=mcnote.width%10*-1+10
                else
                    mcnote.arrow=mcnote.width%10+10
                end
                if mirrorbool then
                    mcnote.arrow=(mcnote.arrow-10)*-1+10
                end
            elseif mcnote.type==7 then
                if mcnote.segments[0].time==0 then
                    notecombolist[mcnote.extra]=0
                else
                    local dur=math.floor((mcnote.segments[0].time+2)*BpmList[1].bpm/1250)
                    notecount=notecount+dur//12+1
                    notecombolist[mcnote.extra]=dur//12+1
                end
                for k=1,mcnote.segments.Length do
                    mcnote.segments[k-1].x=x2c(mcnotex+mcnote.segments[k-1].x)
                end
                if mcnote.segments.Length==1 and mcnote.x==mcnote.segments[0].x then
                    mcnote.type=2
                else
                    if mcnote.segments.Length==1 then
                        local snote={
                            type=22,
                            nid=mcnote.nid,
                            extra=mcnote.extra,
                            x=mcnote.x,
                            y=101,
                            time=mcnote.time,
                            endtime=mcnote.time+mcnote.segments[0].time,
                            arrow=mcnote.segments[0].x-mcnote.x+10,
                            segments={[1]={x=mcnote.x}},
                        }
                        Note:AddVirtual(snote)
                        notecount=notecount+1
                        notecombolist[mcnote.extra]=notecombolist[mcnote.extra]+1
                    else
                        if mcnote.segments[0].time==0 then
                            snote={
                                type=10,
                                nid=mcnote.nid,
                                extra=mcnote.extra,
                                x=mcnote.x,
                                y=101,
                                time=mcnote.time,
                                arrow=mcnote.segments[0].x-mcnote.x+10,
                            }
                        else
                            snote={
                                type=12,
                                nid=mcnote.nid,
                                extra=mcnote.extra,
                                x=mcnote.x,
                                y=101,
                                time=mcnote.time,
                                endtime=mcnote.time+mcnote.segments[0].time,
                                arrow=mcnote.segments[0].x-mcnote.x+10,
                                segments={[1]={x=mcnote.x}},
                            }
                        end
                        Note:AddVirtual(snote)
                        ssnote={}
                        local sgeh=mcnote.segments.Length
                        for k=1,sgeh-1 do
                            local dur=math.floor((mcnote.segments[k].time-mcnote.segments[k-1].time+2)*BpmList[1].bpm/1250)
                            notecount=notecount+dur//12+1
                            notecombolist[mcnote.extra]=notecombolist[mcnote.extra]+dur//12+1
                        end
                        ssnote[sgeh-1]={
                            type=32,
                            nid=mcnote.nid,
                            extra=mcnote.extra,
                            x=mcnote.segments[sgeh-2].x,
                            y=100+sgeh,
                            time=mcnote.time+mcnote.segments[sgeh-2].time,
                            endtime=mcnote.time+mcnote.segments[sgeh-1].time,
                        }
                        if mcnote.segments[sgeh-2].x~=mcnote.segments[sgeh-1].x then
                            notecount=notecount+1
                            notecombolist[mcnote.extra]=notecombolist[mcnote.extra]+1
                            ssnote[sgeh-1].type=22
                            ssnote[sgeh-1].segments={[1]={x=mcnote.segments[sgeh-2].x}}
                            ssnote[sgeh-1].arrow=mcnote.segments[sgeh-1].x-mcnote.segments[sgeh-2].x+10
                        end
                        if sgeh>=3 then
                            for k=1,sgeh-2 do
                                ssnote[k]={
                                    type=12,
                                    nid=mcnote.nid,
                                    extra=mcnote.extra,
                                    x=mcnote.segments[k-1].x,
                                    y=100+k+1,
                                    time=mcnote.time+mcnote.segments[k-1].time,
                                    endtime=mcnote.time+mcnote.segments[k].time,
                                    arrow=mcnote.segments[k].x-mcnote.segments[k-1].x+10,
                                    segments={[1]={x=mcnote.segments[k-1].x}},
                                }
                            end
                        end
                        if mcnote.segments[0].time==0 then
                            ssnote[1].type=ssnote[1].type-1
                        end
                        for k=1,#ssnote do
                            Note:AddVirtual(ssnote[k])
                        end
                    end
                end
            end
        end
        uimaxcombo.Text="0/"..notecount.."\n0.00%(D)"
		if notecount<=20 then
		    maxscore=200*notecount
		elseif notecount>20 and notecount<=50 then
		    maxscore=332*notecount-2640
		elseif notecount>50 and notecount<=100 then
		    maxscore=466*notecount-9340
		else
		    maxscore=600*notecount-22740
		end
		maxstvalue=notecount*0.4
		stvalue=maxstvalue*math.min(Module:GetNumber("初始ST能量"),30)*0.01
    end    
end
function x2c(x)
    if KNum==4 then
        cx=x//64+1
    elseif KNum==5 then
        cx=x//51+1
    else
        cx=x//43+1
    end
    return cx
end
function x2a(w)
    if KNum==4 then
        cw=w//31-1
    elseif KNum==5 then
        cw=w//25-1
    else
        cw=w//21-1
    end
    return cw
end
function OnDrawNote(note)
    
    if (note.y<10) then
        return
    end
    local notetruex=note.x
    if (note.type<=7 and note.y<200) then
        note.y=200
        table.insert(notelist[notetruex],note)
        if (#notelist[notetruex]>1 and notelist[notetruex][#notelist[notetruex]].time<notelist[notetruex][#notelist[notetruex]-1].time) then
            table.sort(notelist[notetruex],function(a,b)
                return a.time<b.time
            end)
        end
        if (notecurready[notetruex]==5000000) then
            local time=Game:Time()
            if time-note.time<=200*judgescale then
                notecur[notetruex]=#notelist[notetruex]
                notecurready[notetruex]=time
            end
        end
    end
    
	--calculate texture index based on track
    if (noteorder<-29000) then
        noteorder=30000
    end
    if (note.type==1) then
	--tap
        mod=Note:GetNoteModule("type-tap","group")
        tap=mod:AddSprite("tap",KNum.."note"..notetruex..".png")
        tap.Alpha=100
        tap.Order=noteorder
        noterorder=noteorder-1
    elseif (note.type==5) then
	--flick
        mod=Note:GetNoteModule("type-flick","group")
        flickhead=mod:AddSprite("flick",KNum.."notel"..notetruex..".png")
        if ((note.arrow-10)<0) then
            flicktail=mod:AddSprite("flicktail","tailsl.png")
        else
            flicktail=mod:AddSprite("flicktail","tailsr.png")
        end
        flickhead.Alpha=100
        flicktail:SetSlice(240,0,240,0)
        flicktail.Alpha=100
        flicktail.Width=259+math.abs(1680/KNum*(note.arrow-10))
        flicktail.Height=240
        flickhead.Order=noteorder
        noteorder=noteorder-1
        flicktail.Order=noteorder
        noteorder=noteorder-1
    elseif (note.type==2) then
	--LN
        mod=Note:GetNoteModule("type-hold","group")
        notebody=mod:AddGrid("body","notebody.png")
        notebody.Alpha=100
        notehead=mod:AddSprite("head",KNum.."notel"..notetruex..".png")
        notehead.Width=NoteW
        notehead.Height=116
        notetail=mod:AddSprite("tail","tail.png","body")
        notetail.Width=520
        notetail.Height=240
        notehead.Alpha=100
        notetail.Alpha=100
        notehead.Order=noteorder
        noteorder=noteorder-1
        notetail.Order=noteorder
        noteorder=noteorder-1
        notebody.Order=noteorder
        noteorder=noteorder-1
    elseif (note.type==7) then
        mod=Note:GetNoteModule("type-slidehead","group")
        head=mod:AddSprite("head",KNum.."notel"..notetruex..".png")
        head.Alpha=100
        head.X=0
        head.Order=noteorder+note.segments.Length*2+3
        noteorder=noteorder-1
    elseif (note.type==10) then
    --Vnote,head with a flick
        mod=Note:GetNoteModule("type-startflick","group")
        fbody=mod:AddSprite("fbody","notefbody.png")
        fbody:SetSlice(240,0,240,0)
        fbody.Width=259+math.abs(1680/KNum*(note.arrow-10))
        fbody.Height=240
        fbody.Alpha=100
        fbody.Order=noteorder-6
        noteorder=noteorder-7
    elseif (note.type==11 or note.type==12) then
    --Vnote,body
        mod=Note:GetNoteModule("type-slide","group")
        notebody=mod:AddGrid("body","notebody.png")
        notebody.Alpha=100
        if (note.arrow-10==0) then
            fbody=mod:AddSprite("fbody","notefbody0.png","body")
            fbody:SetSlice(0,0,0,0)
            fbody.Width=240
        else
            fbody=mod:AddSprite("fbody","notefbody.png","body")
            fbody:SetSlice(240,0,240,0)
            fbody.Width=259+math.abs(1680/KNum*(note.arrow-10+SegX(note,0)-note.x))
        end
        fbody.Height=240
        fbody.Alpha=100
        if (note.type==12 and note.y==101) then
            fbody.Order=noteorder-6
            noteorder=noteorder-7
            notebody.Order=noteorder
            noteorder=noteorder-1
        else
            fbody.Order=noteorder
            noteorder=noteorder-1
            notebody.Order=noteorder
            noteorder=noteorder-1
        end
    elseif (note.type==21 or note.type==22) then
    --vnote,endbody with flick
        mod=Note:GetNoteModule("type-endflick","group")
        notebody=mod:AddGrid("body","notebody.png")
        notebody.Alpha=100
        if (note.arrow-10==0) then
            if (SegX(note,0)-note.x<0) then
                tail=mod:AddSprite("tail","tailsl0.png","body")
            else
                tail=mod:AddSprite("tail","tailsr0.png","body")
            end
            tail:SetSlice(0,0,0,0)
            tail.Width=240
        else
            if ((note.arrow-10+SegX(note,0)-note.x)<0) then
            tail=mod:AddSprite("tail","tailsl.png","body")
            else
                tail=mod:AddSprite("tail","tailsr.png","body")
            end
            tail:SetSlice(240,0,240,0)
            tail.Width=259+math.abs(1680/KNum*(note.arrow-10))
        end
        tail.Height=240
        tail.Alpha=100
        if (note.type==22 and note.y==101) then
            tail.Order=noteorder-6
            noteorder=noteorder-7
            notebody.Order=noteorder
            noteorder=noteorder-1
        else
            tail.Order=noteorder
            noteorder=noteorder-1
            notebody.Order=noteorder
            noteorder=noteorder-1
        end
    elseif (note.type==31 or note.type==32) then
    --vnote,endbody
        mod=Note:GetNoteModule("type-endhold","group")
        notebody=mod:AddGrid("body","notebody.png")
        notebody.Alpha=100
        tail=mod:AddSprite("tail","tail.png","body")
        tail.Width=520
        tail.Height=240
        tail.Alpha=100
        tail.Order=noteorder
        noteorder=noteorder-1
        notebody.Order=noteorder
        noteorder=noteorder-1
    end
end
function OnNoteMove(note,mod,p)
    
    for i=1,p.Length do
        xv[i]=(18883*math.max(0 ,p[i-1])^3-2340)/16543
        xvhit[i]=math.min(1,xv[i])
        notescale[i]=25.9/210+(1-25.9/210)*xv[i]
        notescalehit[i]=math.min(1,notescale[i])
    end
    if (note.type==1) then
	--tap
        mod.X=width/2+TrackTX(KNum,SegX(note))*notescale[1]
        mod.Y=932*(1-xv[1])
        tap=mod:GetChild("tap")
        tap.Width=NoteW*notescale[1]
        tap.Height=116*notescale[1]
        if (hitbreakid[SegX(note)]==note.nid) then
            hitbreakid[SegX(note)]=-1
            tap=mod:AddSprite("tap",KNum.."notelbreak"..SegX(note)..".png")
        elseif (hitnoteid[SegX(note)]==note.nid) then
            hitnoteid[SegX(note)]=-1
            tap.Alpha=0
        end
    elseif (note.type==5) then
	--flick
        flickhead=mod:GetChild("flick")
        flicktail=mod:GetChild("flicktail")
        if (flicknoteid[note.nid]==note.nid) then
		--if flick complete??
            flickhead.Alpha=0
            flicktail.Alpha=0
        elseif (flickbreakid[note.nid]~=nil or flickstateid[note.nid]~=note.nid) then
		-- if flick not in progress 
            if (flickbreakid[note.nid]==note.nid) then
			--if flick break, replace texture
                flickbreakid[note.nid]=-1
                flickhead=mod:AddSprite("flick",KNum.."notelbreak"..SegX(note)..".png")
                if ((note.arrow-10)<0) then
				--flick left/right
                    flicktail=mod:AddSprite("flicktail","tailslbreak.png")
                else
                    flicktail=mod:AddSprite("flicktail","tailsrbreak.png")
                end
            end
			--set note scale & position
            mod.X=width/2+TrackTX(KNum,SegX(note))*notescale[1]
            mod.Y=932*(1-xv[1])
            flickhead.Width=NoteW*notescale[1]
            flickhead.Height=116*notescale[1]
            flicktail.X=840/KNum*(note.arrow-10)*notescale[1]
            flicktail.Scale=notescale[1]
        elseif (flickstateid[note.nid]==note.nid) then
		-- if flick in progress, set position to judgeline
            mod.X=width/2+TrackTX(KNum,SegX(note))
            mod.Y=0
            flickhead.Width=NoteW
            flickhead.Height=116
            flicktail.X=840/KNum*(note.arrow-10)
            flicktail.Scale=1
        end
    elseif (note.type==2) then
	--LN
        head=mod:GetChild("head")
        tail=mod:GetChild("tail")
        body=mod:GetChild("body")
		--set LN textures and scaling
        if (holdbreakid[note.extra]~=nil or holdnoteid[note.extra]~=note.extra) then
		--if hold not in progress
            if (holdbreakid[note.extra]==note.extra) then
			--if LN break, replace textures
                holdbreakid[note.extra]=-1
                head=mod:AddSprite("head",KNum.."notelbreak"..SegX(note)..".png")
                body=mod:AddGrid("body","notebodybreak.png")
                tail=mod:AddSprite("tail","tailbreak.png","body")
            end
            mod.X=width/2+TrackTX(KNum,SegX(note))*notescale[1]
            mod.Y=932*(1-xv[1])
            head.Width=NoteW*notescale[1]
            head.Height=116*notescale[1]
            tail.X=TrackTX(KNum,SegX(note))*notescale[2]-TrackTX(KNum,SegX(note))*notescale[1]
            tail.Y=932*(xv[1]-xv[2])
            tail.Scale=notescale[2]
            body:SetVertex(0,-210*notescale[1],0)
            body:SetVertex(1,210*notescale[1],0)
            body:SetVertex(2,(TrackTX(KNum,SegX(note))-210)*notescale[2]-TrackTX(KNum,SegX(note))*notescale[1],932*(xv[1]-xv[2]))
            body:SetVertex(3,(TrackTX(KNum,SegX(note))+210)*notescale[2]-TrackTX(KNum,SegX(note))*notescale[1],932*(xv[1]-xv[2]))
        else
            mod.X=width/2+TrackTX(KNum,SegX(note))
            mod.Y=0
            head.Width=NoteW
            head.Height=116
            tail.Y=932*(1-xvhit[2])
            tail.X=TrackTX(KNum,SegX(note))*notescalehit[2]-TrackTX(KNum,SegX(note))
            tail.Scale=notescalehit[2]
            body:SetVertex(0,-210,0)
            body:SetVertex(1,210,0)
            body:SetVertex(2,(TrackTX(KNum,SegX(note))-210)*notescalehit[2]-TrackTX(KNum,SegX(note)),932*(1-xvhit[2]))
            body:SetVertex(3,(TrackTX(KNum,SegX(note))+210)*notescalehit[2]-TrackTX(KNum,SegX(note)),932*(1-xvhit[2]))
            if (holdvalue[note.extra]>1) then
                head.Alpha=0
                tail.Alpha=0
                body.Alpha=0
            end
        end
    elseif (note.type==7) then
        head=mod:GetChild("head")
        if (holdbreakid[note.extra]~=nil or holdnoteid[note.extra]~=note.extra) then
            if (holdbreakid[note.extra]==note.extra) then
			--if LN break, replace textures
                head=mod:AddSprite("head",KNum.."notelbreak"..SegX(note)..".png")
            end
            mod.X=width/2+TrackTX(KNum,SegX(note))*notescale[1]
            mod.Y=932*(1-xv[1])
            head.Width=NoteW*notescale[1]
            head.Height=116*notescale[1]
        else
            mod.X=width/2+TrackTX(KNum,SegX(note))
            mod.Y=0
            head.Width=NoteW
            head.Height=116
            if (holdvalue[note.extra]>=2 and holdvalue[note.extra]~=9999) then
                head=mod:AddSprite("head",KNum.."notel"..SegX(note,holdvalue[note.extra]-2)..".png")
                head.X=TrackTX(KNum,SegX(note,holdvalue[note.extra]-2))-TrackTX(KNum,SegX(note))
            elseif (holdvalue[note.extra]==9999) then
                head.Alpha=0
            end
        end
    elseif (note.type==10) then
        fbody=mod:GetChild("fbody")
        if (holdbreakid[note.extra]~=nil or holdnoteid[note.extra]~=note.extra) then
            if (holdbreakid[note.extra]==note.extra) then
                fbody=mod:AddSprite("fbody","notefbodybreak.png")
            end
            mod.X=width/2+TrackTX(KNum,SegX(note))*notescale[1]
            mod.Y=932*(1-xv[1])
            fbody.X=840/KNum*(note.arrow-10)*notescale[1]
            fbody.Scale=notescale[1]
        else
            mod.X=width/2+TrackTX(KNum,SegX(note))
            mod.Y=0
            fbody.X=840/KNum*(note.arrow-10)
            fbody.Scale=1
            if (holdvalue[note.extra]>1) then
                fbody.Alpha=0
            end
        end
    elseif (note.type==11 or note.type==12) then
        body=mod:GetChild("body")
        fbody=mod:GetChild("fbody")
        if (holdbreakid[note.extra]~=nil or holdnoteid[note.extra]~=note.extra) then
            if (holdbreakid[note.extra]==note.extra) then
                body=mod:AddGrid("body","notebodybreak.png")
                if (note.arrow-10==0) then
                    fbody=mod:AddSprite("fbody","notefbody0break.png","body")
                else
                    fbody=mod:AddSprite("fbody","notefbodybreak.png","body")
                end
            end
            mod.X=width/2+TrackTX(KNum,SegX(note))*notescale[1]
            mod.Y=932*(1-xv[1])
            fbody.X=TrackTX(KNum,SegX(note,0)+(note.arrow-10)/2)*notescale[2]-TrackTX(KNum,SegX(note))*notescale[1]
            fbody.Y=932*(xv[1]-xv[2])
            fbody.Scale=notescale[2]
            body:SetVertex(0,-210*notescale[1],0)
            body:SetVertex(1,210*notescale[1],0)
            body:SetVertex(2,(TrackTX(KNum,SegX(note,0))-210)*notescale[2]-TrackTX(KNum,SegX(note))*notescale[1],932*(xv[1]-xv[2]))
            body:SetVertex(3,(TrackTX(KNum,SegX(note,0))+210)*notescale[2]-TrackTX(KNum,SegX(note))*notescale[1],932*(xv[1]-xv[2]))
        else
            if (holdvalue[note.extra]<note.y-100 and note.type==12) then
                mod.X=width/2+TrackTX(KNum,SegX(note))*notescalehit[1]
                mod.Y=932*(1-xvhit[1])
                fbody.X=TrackTX(KNum,SegX(note,0)+(note.arrow-10)/2)*notescalehit[2]-TrackTX(KNum,SegX(note))*notescalehit[1]
                fbody.Y=932*(xvhit[1]-xvhit[2])
                fbody.Scale=notescalehit[2]
                body:SetVertex(0,-210*notescalehit[1],0)
                body:SetVertex(1,210*notescalehit[1],0)
                body:SetVertex(2,(TrackTX(KNum,SegX(note,0))-210)*notescalehit[2]-TrackTX(KNum,SegX(note))*notescalehit[1],932*(xvhit[1]-xvhit[2]))
                body:SetVertex(3,(TrackTX(KNum,SegX(note,0))+210)*notescalehit[2]-TrackTX(KNum,SegX(note))*notescalehit[1],932*(xvhit[1]-xvhit[2]))
            elseif (holdvalue[note.extra]>note.y-100) then
                fbody.Alpha=0
                body.Alpha=0
            else
                mod.X=width/2+TrackTX(KNum,SegX(note))
                mod.Y=0
                fbody.X=TrackTX(KNum,SegX(note,0)+(note.arrow-10)/2)*notescalehit[2]-TrackTX(KNum,SegX(note))
                fbody.Y=932*(1-xvhit[2])
                fbody.Scale=notescalehit[2]
                body:SetVertex(0,-210,0)
                body:SetVertex(1,210,0)
                body:SetVertex(2,(TrackTX(KNum,SegX(note,0))-210)*notescalehit[2]-TrackTX(KNum,SegX(note)),932*(1-xvhit[2]))
                body:SetVertex(3,(TrackTX(KNum,SegX(note,0))+210)*notescalehit[2]-TrackTX(KNum,SegX(note)),932*(1-xvhit[2]))
            end
        end
    elseif (note.type==21 or note.type==22) then
        body=mod:GetChild("body")
        tail=mod:GetChild("tail")
        if (holdbreakid[note.extra]~=nil or holdnoteid[note.extra]~=note.extra) then
            if (holdbreakid[note.extra]==note.extra) then
                body=mod:AddGrid("body","notebodybreak.png")
                if (note.arrow-10==0) then
                    if (SegX(note,0)-note.x<0) then
                        tail=mod:AddSprite("tail","tailsl0break.png","body")
                    else
                        tail=mod:AddSprite("tail","tailsr0break.png","body")
                    end
                else
                    if (SegX(note,0)-note.x+note.arrow-10<0) then
                        tail=mod:AddSprite("tail","tailslbreak.png","body")
                    else
                        tail=mod:AddSprite("tail","tailsrbreak.png","body")
                    end
                end
            end
            mod.X=width/2+TrackTX(KNum,SegX(note))*notescale[1]
            mod.Y=932*(1-xv[1])
            tail.X=TrackTX(KNum,SegX(note,0)+(note.arrow-10)/2)*notescale[2]-TrackTX(KNum,SegX(note))*notescale[1]
            tail.Y=932*(xv[1]-xv[2])
            tail.Scale=notescale[2]
            body:SetVertex(0,-210*notescale[1],0)
            body:SetVertex(1,210*notescale[1],0)
            body:SetVertex(2,(TrackTX(KNum,SegX(note,0))-210)*notescale[2]-TrackTX(KNum,SegX(note))*notescale[1],932*(xv[1]-xv[2]))
            body:SetVertex(3,(TrackTX(KNum,SegX(note,0))+210)*notescale[2]-TrackTX(KNum,SegX(note))*notescale[1],932*(xv[1]-xv[2]))
        else
            if (holdvalue[note.extra]<note.y-100 and note.type==22) then
                mod.X=width/2+TrackTX(KNum,SegX(note))*notescalehit[1]
                mod.Y=932*(1-xvhit[1])
                tail.X=TrackTX(KNum,SegX(note,0)+(note.arrow-10)/2)*notescalehit[2]-TrackTX(KNum,SegX(note))*notescalehit[1]
                tail.Y=932*(xvhit[1]-xvhit[2])
                tail.Scale=notescalehit[2]
                body:SetVertex(0,-210*notescalehit[1],0)
                body:SetVertex(1,210*notescalehit[1],0)
                body:SetVertex(2,(TrackTX(KNum,SegX(note,0))-210)*notescalehit[2]-TrackTX(KNum,SegX(note))*notescalehit[1],932*(xvhit[1]-xvhit[2]))
                body:SetVertex(3,(TrackTX(KNum,SegX(note,0))+210)*notescalehit[2]-TrackTX(KNum,SegX(note))*notescalehit[1],932*(xvhit[1]-xvhit[2]))
            elseif (holdvalue[note.extra]>note.y-100) then
                tail.Alpha=0
                body.Alpha=0
            else
                mod.X=width/2+TrackTX(KNum,SegX(note))
                mod.Y=0
                tail.X=TrackTX(KNum,SegX(note,0)+(note.arrow-10)/2)*notescalehit[2]-TrackTX(KNum,SegX(note))
                tail.Y=932*(1-xvhit[2])
                tail.Scale=notescalehit[2]
                body:SetVertex(0,-210,0)
                body:SetVertex(1,210,0)
                body:SetVertex(2,(TrackTX(KNum,SegX(note,0))-210)*notescalehit[2]-TrackTX(KNum,SegX(note)),932*(1-xvhit[2]))
                body:SetVertex(3,(TrackTX(KNum,SegX(note,0))+210)*notescalehit[2]-TrackTX(KNum,SegX(note)),932*(1-xvhit[2]))
            end
        end
    elseif (note.type==31 or note.type==32) then
        body=mod:GetChild("body")
        tail=mod:GetChild("tail")
        if (holdbreakid[note.extra]~=nil or holdnoteid[note.extra]~=note.extra) then
            if (holdbreakid[note.extra]==note.extra) then
                body=mod:AddGrid("body","notebodybreak.png")
                tail=mod:AddSprite("tail","tailbreak.png","body")
            end
            mod.X=width/2+TrackTX(KNum,SegX(note))*notescale[1]
            mod.Y=932*(1-xv[1])
            tail.X=TrackTX(KNum,SegX(note))*notescale[2]-TrackTX(KNum,SegX(note))*notescale[1]
            tail.Y=932*(xv[1]-xv[2])
            tail.Scale=notescale[2]
            body:SetVertex(0,-210*notescale[1],0)
            body:SetVertex(1,210*notescale[1],0)
            body:SetVertex(2,(TrackTX(KNum,SegX(note))-210)*notescale[2]-TrackTX(KNum,SegX(note))*notescale[1],932*(xv[1]-xv[2]))
            body:SetVertex(3,(TrackTX(KNum,SegX(note))+210)*notescale[2]-TrackTX(KNum,SegX(note))*notescale[1],932*(xv[1]-xv[2]))
        else
            if (holdvalue[note.extra]<note.y-100 and note.type==32) then
                mod.X=width/2+TrackTX(KNum,SegX(note))*notescalehit[1]
                mod.Y=932*(1-xvhit[1])
                tail.X=TrackTX(KNum,SegX(note))*notescalehit[2]-TrackTX(KNum,SegX(note))*notescalehit[1]
                tail.Y=932*(xvhit[1]-xvhit[2])
                tail.Scale=notescalehit[2]
                body:SetVertex(0,-210*notescalehit[1],0)
                body:SetVertex(1,210*notescalehit[1],0)
                body:SetVertex(2,(TrackTX(KNum,SegX(note))-210)*notescalehit[2]-TrackTX(KNum,SegX(note))*notescalehit[1],932*(xvhit[1]-xvhit[2]))
                body:SetVertex(3,(TrackTX(KNum,SegX(note))+210)*notescalehit[2]-TrackTX(KNum,SegX(note))*notescalehit[1],932*(xvhit[1]-xvhit[2]))
            elseif (holdvalue[note.extra]>note.y-100) then
                tail.Alpha=0
                body.Alpha=0
            else
                mod.X=width/2+TrackTX(KNum,SegX(note))
                mod.Y=0
                tail.X=TrackTX(KNum,SegX(note))*notescalehit[2]-TrackTX(KNum,SegX(note))
                tail.Y=932*(1-xvhit[2])
                tail.Scale=notescalehit[2]
                body:SetVertex(0,-210,0)
                body:SetVertex(1,210,0)
                body:SetVertex(2,(TrackTX(KNum,SegX(note))-210)*notescalehit[2]-TrackTX(KNum,SegX(note)),932*(1-xvhit[2]))
                body:SetVertex(3,(TrackTX(KNum,SegX(note))+210)*notescalehit[2]-TrackTX(KNum,SegX(note)),932*(1-xvhit[2]))
            end
        end
    end
end
function OnInput()
    local itemflag=0
	local tempjudgevalue=0
    inevt=Game:InputEvent()
    hitx=inevt:HitX()
    hity=inevt:HitY()
    type=inevt:Type()
    time=Game:Time()
    source=inevt:Source()
	if (time<=-1500) then
	    return
	end
	if (type==1 and (STbool=="OpenST" or STbool=="Open2ST")) then
	    if (math.abs(hitx-feverbutton_X)<=feverbutton_R and math.abs(hity-feverbutton_Y)<=feverbutton_R) then
	        if (STstate==2) then
	            uifeverbutton.Alpha=0
	            STstate=1
	            STstate2=0
	            FeverAnimation(time)
	            sttime=time
	            uifevertrack.Alpha=30
	            uimaxcombo:SetColor(255,255,0)
	        elseif (STstate==1 and STstate2==1) then
	            uifeverbutton.Alpha=0
	            STstate=0
	            STstate2=0
	            uifevertrack.Alpha=0
	            uimaxcombo:SetColor(255,255,255)
	        end
	    end
	end
	--track hitbox (triangle),top vertex at (50%,1211.8)
	if (KNum==4) then
        if (hity-1080-131*trackscale<=71/70*(hitx-width/2)/gscale*trackscale and hity-1080-131*trackscale>71/28*(hitx-width/2)/gscale*trackscale) then
            notex=1
        elseif (hity-1080-131*trackscale<=71/28*(hitx-width/2)/gscale*trackscale and hitx<width/2) then
            notex=2
        elseif (hitx>=width/2 and hity-1080-131*trackscale<-71/28*(hitx-width/2)/gscale*trackscale) then
            notex=3
        elseif (hity-1080-131*trackscale>=-71/28*(hitx-width/2)/gscale*trackscale and hity-1080-131*trackscale<-71/70*(hitx-width/2)/gscale*trackscale) then
            notex=4
        else
            notex=5
        end
    elseif (KNum==5) then
        if (hity-1080-131*trackscale<=71/70*(hitx-width/2)/gscale*trackscale and hity-1080-131*trackscale>71/33.6*(hitx-width/2)/gscale*trackscale) then
            notex=1
        elseif (hity-1080-131*trackscale<=71/33.6*(hitx-width/2)/gscale*trackscale and hity-1080-131*trackscale>71/11.2*(hitx-width/2)/gscale*trackscale) then
            notex=2
        elseif (hity-1080-131*trackscale<=71/11.2*(hitx-width/2)/gscale*trackscale and hity-1080-131*trackscale<-71/11.2*(hitx-width/2)/gscale*trackscale) then
            notex=3
        elseif (hity-1080-131*trackscale>=-71/11.2*(hitx-width/2)/gscale*trackscale and hity-1080-131*trackscale<-71/33.6*(hitx-width/2)/gscale*trackscale) then
            notex=4
        elseif (hity-1080-131*trackscale>=-71/33.6*(hitx-width/2)/gscale*trackscale and hity-1080-131*trackscale<=-71/70*(hitx-width/2)/gscale*trackscale) then
            notex=5
        else
            notex=6
        end
    else
        if (hity-1080-131*trackscale<=71/70*(hitx-width/2)/gscale*trackscale and hity-1080-131*trackscale>71/28*0.75*(hitx-width/2)/gscale*trackscale) then
            notex=1
        elseif (hity-1080-131*trackscale<=71/28*0.75*(hitx-width/2)/gscale*trackscale and hity-1080-131*trackscale>71/28*1.5*(hitx-width/2)/gscale*trackscale) then
            notex=2
        elseif (hity-1080-131*trackscale<=71/28*1.5*(hitx-width/2)/gscale*trackscale and hitx<width/2) then
            notex=3
        elseif (hitx>=width/2 and hity-1080-131*trackscale<-71/28*1.5*(hitx-width/2)/gscale*trackscale) then
            notex=4
        elseif (hity-1080-131*trackscale>=-71/28*1.5*(hitx-width/2)/gscale*trackscale and hity-1080-131*trackscale<-71/28*0.75*(hitx-width/2)/gscale*trackscale) then
            notex=5
        elseif (hity-1080-131*trackscale>=-71/28*0.75*(hitx-width/2)/gscale*trackscale and hity-1080-131*trackscale<-71/70*(hitx-width/2)/gscale*trackscale) then
            notex=6
        else
            notex=7
        end
    end
    if (type==1 or type==2) then
	-- tap or move (key down)
        if (inr[source+1][notex]~=1) then
		-- set press state if current finger is not occupied
            inr[source+1][notex]=1
            lastfingerid[source+1]=fingerid[source+1]
            fingerid[source+1]=notex
            if (notex==KNum+1) then return;end
            if (fingerid[source+1]~=lastfingerid[source+1]) then
			-- if finger at different track, reset press state
                inr[source+1][lastfingerid[source+1]]=3
                if (lastfingerid[source+1]~=0 and lastfingerid[source+1]~=KNum+1) then
				-- if is a flick
                    if (flickstate[source+1]~=0) then
                        if (fingerid[source+1]==SegX(flicknote[source+1])+flicknote[source+1].arrow-10) then
                            flickstate[source+1]=0
                            flicknoteid[flicknote[source+1].nid]=flicknote[source+1].nid
                            flickoffset=time-flicktime[source+1]
                            FlickJudge()
                            JudgeModule()
                            Combo()
                            uicombo.Text=combovalue
                            ScoreBonus()
                            Score()
                            Acc()
                            if (judgevalue~=4) then
                                uihit[SegX(flicknote[source+1])+flicknote[source+1].arrow-10]:Play()
                                if Audiobool then Audio:Play(Flick_hitsound,Value_hitsound);end
                            end
                        end
                    end
                    if (holdmap[source+1]>0 and holdstate[holdmap[source+1]]~=0 and holdstate[holdmap[source+1]]~=9999 and (not (holdstate[holdmap[source+1]]==holdnote[holdmap[source+1]].segments.Length and holdnote[holdmap[source+1]].segments.Length>1 and SegX(holdnote[holdmap[source+1]],-2)==SegX(holdnote[holdmap[source+1]],-1)))) then
					    -- if LN is hold and not broken, and is not the last segment of a LN ending with a hold
                        if (math.abs(fingerid[source+1]-SegX(holdnote[holdmap[source+1]],holdstate[holdmap[source+1]]-1))>math.abs(lastfingerid[source+1]-SegX(holdnote[holdmap[source+1]],holdstate[holdmap[source+1]]-1))) then
                        --if finger out of the hitbox
							if (holdnote[holdmap[source+1]].type==2) then
							--single hold LN
                                if (time-(holdnote[holdmap[source+1]].time+holdnote[holdmap[source+1]].segments[0].time)<-200*judgescale) then
								--if finger move away from current track too early, set a miss state
								    if (itemhold>0) then
									    itemhold=itemhold-1
										itemholdstate[holdmap[source+1]]=1
									    holdmapinverse[holdmap[source+1]]=0						
							            holdmap[source+1]=0
									else
										uihitl[holdmap[source+1]].Alpha=0
										holdstate[holdmap[source+1]]=0
										holdbreakid[holdnote[holdmap[source+1]].extra]=holdnote[holdmap[source+1]].extra

										offset=math.abs(judgeoffset.g)+100
										if (STstate==0) then
										    Judge()
										    JudgeModule()
										    Combo()
										    uicombo.Text=combovalue
										    ScoreBonus()
										    Score()
										    acccount=acccount-1
										    Acc()
										    stvalue=0
										elseif (STstate==1) then
										    uiacc.Text=string.format("%.2f",accvalue/acccount)
										else
										    Judge()
										    JudgeModule()
										    Combo()
										    uicombo.Text=combovalue
										    ScoreBonus()
										    Score()
										    acccount=acccount-1
										    Acc()
										end
									end
   
                                else
									--set LN finish state (same as early release)
                                    holdstate[holdmap[source+1]]=9999
                                    holdvalue[holdnote[holdmap[source+1]].extra]=holdstate[holdmap[source+1]]
                                    uihitl[holdmap[source+1]].Alpha=0
                                end
                            else
							--multi-segment LN/ single-segment LN ending with a flick
								if (itemhold>0) then
									itemhold=itemhold-1
									itemholdstate[holdmap[source+1]]=1
									holdmapinverse[holdmap[source+1]]=0						
									holdmap[source+1]=0
								else
									uihitl[holdmap[source+1]].Alpha=0
									holdstate[holdmap[source+1]]=0
									holdbreakid[holdnote[holdmap[source+1]].extra]=holdnote[holdmap[source+1]].extra								
								end
                            end
                        else
						--if finger in hitbox
                            if (math.abs(time-(holdnote[holdmap[source+1]].time+holdnote[holdmap[source+1]].segments[holdstate[holdmap[source+1]]-1].time))>200*judgescale) then
							--gray out if hit too early or too late
								if (itemhold>0) then
									itemhold=itemhold-1
									itemholdstate[holdmap[source+1]]=1
									holdmapinverse[holdmap[source+1]]=0						
									holdmap[source+1]=0
								else
									uihitl[holdmap[source+1]].Alpha=0
									holdstate[holdmap[source+1]]=0
									holdbreakid[holdnote[holdmap[source+1]].extra]=holdnote[holdmap[source+1]].extra								
								end
                                    
                            else
							--if hit on time
                                if (fingerid[source+1]==SegX(holdnote[holdmap[source+1]],holdstate[holdmap[source+1]]-1)) then
								--if finger at the correct track
                                    holdstate[holdmap[source+1]]=holdstate[holdmap[source+1]]+1
                                    holdvalue[holdnote[holdmap[source+1]].extra]=holdstate[holdmap[source+1]]
                                    uihitl[holdmap[source+1]].X=TrackTX(KNum,SegX(holdnote[holdmap[source+1]],holdstate[holdmap[source+1]]-2))*gscale
                                    if Audiobool then Audio:Play(Drag_hitsound,Value_hitsound);end
									--possible logic bug (acccount) if flick miss time <200ms!!!!!!
									lnnotecombo[holdmap[source+1]]=lnnotecombo[holdmap[source+1]]+1
                                    if (holdstate[holdmap[source+1]]<=holdnote[holdmap[source+1]].segments.Length) then
									--comboevent when moving to the next segment (except the flick end)
										holdstatecopy[holdmap[source+1]]=holdstate[holdmap[source+1]]
                                        ljudgevalue=math.floor(judgevalue)
                                        judgevalue=1
                                        judgeevent=1
                                        bestcount=bestcount+1
                                        JudgeModule()
                                        comboevent=1
                                        combovalue=combovalue+1
                                        uicombo.Text=combovalue
                                        ScoreBonus()
                                        Score()
                                        Acc()
                                        if (STstate==0) then
                                            stvalue=stvalue+1.2
                                            if (stvalue>=maxstvalue) then
                                                STstate=2
                                                stvalue=maxstvalue
                                            end
                                        end
                                    else
									--flick judge for LN ending with a flick
                                        uihitl[holdmap[source+1]].Alpha=0
                                        uihit[fingerid[source+1]]:Play()
                                        holdstate[holdmap[source+1]]=9999
                                        holdvalue[holdnote[holdmap[source+1]].extra]=9999
                                        flickoffset=time-holdnote[holdmap[source+1]].time-holdnote[holdmap[source+1]].segments[holdnote[holdmap[source+1]].segments.Length-1].time
                                        FlickJudge()
										tempjudgevalue=judgevalue
                                        JudgeModule()
                                        Combo()
                                        uicombo.Text=combovalue
                                        ScoreBonus()
                                        Score()
                                        Acc()
                                        if Audiobool then Audio:Play(Flick_hitsound,Value_hitsound);end
                                    end
                                end
                            end
						end	
							
						--update when LN ends
						if (holdstate[holdmap[source+1]]==0 or holdstate[holdmap[source+1]]==9999) then
							--update combo
							lncombo=0
							while (seg[holdmap[source+1]]<=holdstatecopy[holdmap[source+1]]) do
							--calculate combo of previous segments      Fuck **This** Update Function and Refresh Rate!!!!!!!!! 
								if (time>=holdnote[holdmap[source+1]].time+holdnote[holdmap[source+1]].segments[seg[holdmap[source+1]]-1].time) then
									combotime=holdnote[holdmap[source+1]].time+holdnote[holdmap[source+1]].segments[seg[holdmap[source+1]]-1].time
									breakflag=0
								else
									combotime=time
									breakflag=1
								end								
									
								if (seg[holdmap[source+1]]==1) then
						            combotime=math.max(combotime,holdnote[holdmap[source+1]].time)
									lncombo=lncombo+math.floor((combotime+2-holdnote[holdmap[source+1]].time)*BpmList[1].bpm/15000)-(holdcombovalue[holdmap[source+1]]-1)
								elseif (seg[holdmap[source+1]]>1) then
									lncombo=lncombo+math.floor((combotime+2-holdnote[holdmap[source+1]].time-holdnote[holdmap[source+1]].segments[seg[holdmap[source+1]]-2].time)*BpmList[1].bpm/15000)-(holdcombovalue[holdmap[source+1]]-1)
								end
								--break the loop when finish
								if (breakflag==1) then
									break
								end									
								--reset pointer and counter. If time >= LN endtime, seg[holdmap[source+1]]=holdstatecopy[holdmap[source+1]]+1=holdnote[holdmap[source+1]].segments.Length+1
								--dtime[source+1]=holdnote[holdmap[source+1]].time+holdnote[holdmap[source+1]].segments[seg[holdmap[source+1]]-1].time
								holdcombovalue[holdmap[source+1]]=1
								seg[holdmap[source+1]]=seg[holdmap[source+1]]+1
							end

							-- compensate for early flick/release (out of the hitbox of a tail-hold) combo loss (<=1 ), will allow switching on/off later
							itemflag=0
							if (holdstate[holdmap[source+1]]==9999 and time<holdnote[holdmap[source+1]].time+holdnote[holdmap[source+1]].segments[holdnote[holdmap[source+1]].segments.Length-1].time) then
								if(lnnotecount[holdmap[source+1]]>=lnnotecombo[holdmap[source+1]]+lncombo+1) then
									lncombo=lncombo+1
									if(lnnotecount[holdmap[source+1]]>=lnnotecombo[holdmap[source+1]]+lncombo+1 and mg2p>0) then
										itemflag=1
									end									
								end
							end
								
							--update HP, score and combo.
							if (lncombo>0) then
								hpvalue=math.min(hpvalue+lncombo,100)
								lnnotecombo[holdmap[source+1]]=lnnotecombo[holdmap[source+1]]+lncombo+itemflag
								comboevent=1
								--display judgement of the flick!!
								if (tempjudgevalue<=1) then
								    ljudgevalue=math.floor(judgevalue)
								end
								judgevalue=1
								judgeevent=1
								bestcount=bestcount+lncombo
								if (STstate==0) then
								    stvalue=stvalue+lncombo*1.2
								    if (stvalue>=maxstvalue) then
								        STstate=2
								        stvalue=maxstvalue
								    end
								end
								for iteration=1,lncombo do
								    combovalue=combovalue+1
									ScoreBonus()
									Score()
									Acc()				    
								end
								if (itemflag==1) then
								    mg2p=mg2p-1
								    if (STstate==0) then
								        judgevalue=2.3
								        coolcount=coolcount+1
								        stvalue=stvalue+1
								        if (stvalue>=maxstvalue) then
								            STstate=2
								            stvalue=maxstvalue
								        end
								    elseif (STstate==1) then
								        judgevalue=1
								        bestcount=bestcount+1
								    else
								        judgevalue=2.3
								        coolcount=coolcount+1
								    end
								    combovalue=combovalue+1
									ScoreBonus()
									Score()
									Acc()								
								end
								uicombo.Text=combovalue
                                if (tempjudgevalue>1) then
								    judgevalue=tempjudgevalue
								end
                                JudgeModule()								
							end
								
							--update acc
							acccount=acccount+lnnotecount[holdmap[source+1]]-lnnotecombo[holdmap[source+1]]
							uiacc.Text=string.format("%.2f",accvalue/acccount)

							--reset pointer
							seg[holdmap[source+1]]=0
							lnnotecount[holdmap[source+1]]=0
							lnnotecombo[holdmap[source+1]]=0
							holdcombovalue[holdmap[source+1]]=1
							dtime[source+1]=holdnote[holdmap[source+1]].time+holdnote[holdmap[source+1]].segments[holdnote[holdmap[source+1]].segments.Length-1].time


                            --update maxsource and holdmap.
							--print(source.."-")
							--print(table.concat(holdmap, ", "))
                            holdmapinverse[holdmap[source+1]]=0						
							holdmap[source+1]=0
							if (source==maxsource and maxsource>0) then
							    for j=maxsource,1,-1 do
								    if (holdstate[j]~=0 and holdstate[j]~=9999) then
								        maxsource=j-1
								    break
									end
								end
							end


                        end
                    end
                end
            end
            if (fingerban[source+1]==1) then
			--finger banned from triggering hitbox (LN)
                return
            end
            if (notecur[notex]>#notelist[notex]) then
                return
            end
            if (math.abs(time-notelist[notex][notecur[notex]].time)<=200*judgescale) then
                notecur[notex]=notecur[notex]+1
            else
                return
            end
			--find offset, trigger the judgement,disable current finger from triggering (set fingerban)
            offset=time-notelist[notex][notecur[notex]-1].time
            if (notelist[notex][notecur[notex]-1].type==1) then
			--tap
                Play:SetNoteFinish(notelist[notex][notecur[notex]-1].nid)
                Judge()
                JudgeModule()
                Combo()
                uicombo.Text=combovalue
                ScoreBonus()
                Score()
                Acc()
                if (judgevalue~=4) then
                    uihit[notex]:Play()
                    if Audiobool then Audio:Play(Tap_hitsound,Value_hitsound);end
                end
                hitnoteid[notex]=notelist[notex][notecur[notex]-1].nid
            elseif (notelist[notex][notecur[notex]-1].type==5) then
			--flick
                Play:SetNoteFinish(notelist[notex][notecur[notex]-1].nid)
                fingerban[source+1]=1
                flickstateid[notelist[notex][notecur[notex]-1].nid]=notelist[notex][notecur[notex]-1].nid
                flickstate[source+1]=1
                flicktime[source+1]=notelist[notex][notecur[notex]-1].time
                flicknote[source+1]=notelist[notex][notecur[notex]-1]
            elseif (notelist[notex][notecur[notex]-1].type==7 or notelist[notex][notecur[notex]-1].type==2) then
                if Audiobool then Audio:Play(Tap_hitsound,Value_hitsound);end
			--LN head judgement, always set sperfect
				for j=1,20 do
					--find an empty slot to attach to the LN
					if (holdstate[j]==0 or holdstate[j]==9999) then
					    holdmap[source+1]=j
						holdmapinverse[j]=source+1
						if (maxsource+1<j) then
						    maxsource=j-1
						end
						break
					end
				end
				--print(source.."+")
				--print(table.concat(holdmap, ", "))
			
                Play:SetNoteFinish(notelist[notex][notecur[notex]-1].nid)
                fingerban[source+1]=1
				lnnotecount[holdmap[source+1]]=notecombolist[notelist[notex][notecur[notex]-1].extra]
				lnnotecombo[holdmap[source+1]]=0
                holdnoteid[notelist[notex][notecur[notex]-1].extra]=notelist[notex][notecur[notex]-1].extra
                holdnote[holdmap[source+1]]=notelist[notex][notecur[notex]-1]
                holdstate[holdmap[source+1]]=1
				holdstatecopy[holdmap[source+1]]=1
                holdvalue[holdnote[holdmap[source+1]].extra]=1
                uihitl[holdmap[source+1]].X=TrackTX(KNum,notex)*gscale
                uihitl[holdmap[source+1]].Alpha=100
                uihitl[holdmap[source+1]]:Play()
			    --set LN head as reference, initialize combo-related parameters
			    lncombo=0
                dtime[source+1]=holdnote[holdmap[source+1]].time
                seg[holdmap[source+1]]=1
                holdcombovalue[holdmap[source+1]]=1				
                if (notelist[notex][notecur[notex]-1].segments[0].time~=0) then
				--LN head combo for LN starts with a hold
                    lncombo=lncombo+1
                    holdcombovalue[holdmap[source+1]]=0
				end
				--if hitslow, compensate for the missing combo
				if (time>holdnote[holdmap[source+1]].time) then
				    combotime=math.min(time,holdnote[holdmap[source+1]].time+holdnote[holdmap[source+1]].segments[0].time)
				    lncombo=lncombo+math.floor((combotime+2-holdnote[holdmap[source+1]].time)*BpmList[1].bpm/15000)
				end
				--compensate for HP, score and combo
				if (lncombo>0) then
					hpvalue=math.min(hpvalue+lncombo,100)			
					lnnotecombo[holdmap[source+1]]=lnnotecombo[holdmap[source+1]]+lncombo
					holdcombovalue[holdmap[source+1]]=holdcombovalue[holdmap[source+1]]+lncombo
					comboevent=1
					judgeevent=1
					ljudgevalue=math.floor(judgevalue)
					judgevalue=1
					JudgeModule()
					bestcount=bestcount+lncombo
					if (STstate==0) then
					    stvalue=stvalue+lncombo*1.2
					    if (stvalue>=maxstvalue) then
					        STstate=2
					        stvalue=maxstvalue
					    end
					end
					for iteration=1,lncombo do
					    combovalue=combovalue+1
						ScoreBonus()
						Score()
						Acc()				    
					end
					uicombo.Text=combovalue					
				end
				
            end
        end  
    else
	-- finger release
        for i=1,KNum do
            if (inr[source+1][i]~=3) then
                inr[source+1][i]=3
            end
        end
        fingerban[source+1]=0
        fingerid[source+1]=0
        if (holdmap[source+1]>0 and holdstate[holdmap[source+1]]~=0 and holdstate[holdmap[source+1]]~=9999) then
		-- if is held, not broken and not finished
            if (holdnote[holdmap[source+1]].segments.Length==1 and SegX(holdnote[holdmap[source+1]])==SegX(holdnote[holdmap[source+1]],0)) then
			-- if is a single hold LN
                if (time-(holdnote[holdmap[source+1]].time+holdnote[holdmap[source+1]].segments[holdnote[holdmap[source+1]].segments.Length-1].time)<-200*judgescale) then
				-- if release too early
					if (itemhold>0) then
						itemhold=itemhold-1
						itemholdstate[holdmap[source+1]]=1
						holdmapinverse[holdmap[source+1]]=0						
						holdmap[source+1]=0
					else
						uihitl[holdmap[source+1]].Alpha=0
						holdstate[holdmap[source+1]]=0
						holdbreakid[holdnote[holdmap[source+1]].extra]=holdnote[holdmap[source+1]].extra

						offset=math.abs(judgeoffset.g)+100
						if (STstate==0) then
						    Judge()
						    JudgeModule()
						    Combo()
						    uicombo.Text=combovalue
						    ScoreBonus()
						    Score()
						    acccount=acccount-1
						    Acc()
						    stvalue=0
						elseif (STstate==1) then
						    uiacc.Text=string.format("%.2f",accvalue/acccount)
						else
						    Judge()
						    JudgeModule()
						    Combo()
						    uicombo.Text=combovalue
						    ScoreBonus()
						    Score()
						    acccount=acccount-1
						    Acc()
						end
					end
                else
				--set finish state if is held until LN end
                    holdstate[holdmap[source+1]]=9999
                    holdvalue[holdnote[holdmap[source+1]].extra]=holdstate[holdmap[source+1]]
                    uihitl[holdmap[source+1]].Alpha=0
                end
            elseif (holdnote[holdmap[source+1]].segments.Length>1 and SegX(holdnote[holdmap[source+1]],-2)==SegX(holdnote[holdmap[source+1]],-1)) then
            -- multi-segment LN ending with a hold
				if (holdstate[holdmap[source+1]]<holdnote[holdmap[source+1]].segments.Length) then
				-- if not the last segment, gray out 
					if (itemhold>0) then
						itemhold=itemhold-1
						itemholdstate[holdmap[source+1]]=1
						holdmapinverse[holdmap[source+1]]=0						
						holdmap[source+1]=0
					else
						uihitl[holdmap[source+1]].Alpha=0
						holdstate[holdmap[source+1]]=0
						holdbreakid[holdnote[holdmap[source+1]].extra]=holdnote[holdmap[source+1]].extra								
					end
                else
				-- if is the last segment
                    if (time-(holdnote[holdmap[source+1]].time+holdnote[holdmap[source+1]].segments[holdnote[holdmap[source+1]].segments.Length-1].time)<-200*judgescale) then
					-- if release too early
						if (itemhold>0) then
							itemhold=itemhold-1
							itemholdstate[holdmap[source+1]]=1
							holdmapinverse[holdmap[source+1]]=0						
							holdmap[source+1]=0
						else
							uihitl[holdmap[source+1]].Alpha=0
							holdstate[holdmap[source+1]]=0
							holdbreakid[holdnote[holdmap[source+1]].extra]=holdnote[holdmap[source+1]].extra

							offset=math.abs(judgeoffset.g)+100
							if (STstate==0) then
							    Judge()
							    JudgeModule()
							    Combo()
							    uicombo.Text=combovalue
							    ScoreBonus()
							    Score()
							    acccount=acccount-1
							    Acc()
							    stvalue=0
							elseif (STstate==1) then
							    uiacc.Text=string.format("%.2f",accvalue/acccount)
							else
							    Judge()
							    JudgeModule()
							    Combo()
							    uicombo.Text=combovalue
							    ScoreBonus()
							    Score()
							    acccount=acccount-1
							    Acc()
							end
						end
                    else
					--set LN finishstate
                        holdstate[holdmap[source+1]]=9999
                        holdvalue[holdnote[holdmap[source+1]].extra]=holdstate[holdmap[source+1]]
                        uihitl[holdmap[source+1]].Alpha=0
                    end
                end
            else
			-- single/multi-segment LN with a flick end
                if (holdstate[holdmap[source+1]]~=9999) then
				-- gray out if release before LN end
					if (itemhold>0) then
						itemhold=itemhold-1
						itemholdstate[holdmap[source+1]]=1
						holdmapinverse[holdmap[source+1]]=0						
						holdmap[source+1]=0
					else
						uihitl[holdmap[source+1]].Alpha=0
						holdstate[holdmap[source+1]]=0
						holdbreakid[holdnote[holdmap[source+1]].extra]=holdnote[holdmap[source+1]].extra								
					end
                end
            end
			
			--update when LN ends
			if (holdstate[holdmap[source+1]]==0 or holdstate[holdmap[source+1]]==9999) then
				--update combo
				lncombo=0
				while (seg[holdmap[source+1]]<=holdstatecopy[holdmap[source+1]]) do
				--calculate combo of previous segments      Fuck **This** Update Function and Refresh Rate!!!!!!!!! 
					if (time>=holdnote[holdmap[source+1]].time+holdnote[holdmap[source+1]].segments[seg[holdmap[source+1]]-1].time) then
						combotime=holdnote[holdmap[source+1]].time+holdnote[holdmap[source+1]].segments[seg[holdmap[source+1]]-1].time
						breakflag=0
					else
						combotime=time
						breakflag=1
					end								
					
					if (seg[holdmap[source+1]]==1) then
					    combotime=math.max(combotime,holdnote[holdmap[source+1]].time)
						lncombo=lncombo+math.floor((combotime+2-holdnote[holdmap[source+1]].time)*BpmList[1].bpm/15000)-(holdcombovalue[holdmap[source+1]]-1)
					elseif (seg[holdmap[source+1]]>1) then
						lncombo=lncombo+math.floor((combotime+2-holdnote[holdmap[source+1]].time-holdnote[holdmap[source+1]].segments[seg[holdmap[source+1]]-2].time)*BpmList[1].bpm/15000)-(holdcombovalue[holdmap[source+1]]-1)
					end
					--break the loop when finish
					if (breakflag==1) then
						break
					end									
					--reset pointer and counter. If time >= LN endtime, seg[holdmap[source+1]]=holdstatecopy[holdmap[source+1]]+1=holdnote[holdmap[source+1]].segments.Length+1
					--dtime[source+1]=holdnote[holdmap[source+1]].time+holdnote[holdmap[source+1]].segments[seg[holdmap[source+1]]-1].time
					holdcombovalue[holdmap[source+1]]=1
					seg[holdmap[source+1]]=seg[holdmap[source+1]]+1
				end

				-- compensate for early release (LN tail, must be a hold) combo loss (<=1 ), will allow switching on/off later
				itemflag=0
				if (holdstate[holdmap[source+1]]==9999 and time<holdnote[holdmap[source+1]].time+holdnote[holdmap[source+1]].segments[holdnote[holdmap[source+1]].segments.Length-1].time) then
					if(lnnotecount[holdmap[source+1]]>=lnnotecombo[holdmap[source+1]]+lncombo+1) then
						lncombo=lncombo+1
						if(lnnotecount[holdmap[source+1]]>=lnnotecombo[holdmap[source+1]]+lncombo+1 and mg2p>0) then
							itemflag=1
						end									
					end
				end
					
				--update HP, score and combo.
				if (lncombo>0) then
					hpvalue=math.min(hpvalue+lncombo,100)
					lnnotecombo[holdmap[source+1]]=lnnotecombo[holdmap[source+1]]+lncombo+itemflag
					comboevent=1
					ljudgevalue=math.floor(judgevalue)
					judgevalue=1
					judgeevent=1
					bestcount=bestcount+lncombo
					if (STstate==0) then
					    stvalue=stvalue+lncombo*1.2
					    if (stvalue>=maxstvalue) then
					        STstate=2
					        stvalue=maxstvalue
					    end
					end
					for iteration=1,lncombo do
					    combovalue=combovalue+1
						ScoreBonus()
						Score()
						Acc()				    
					end
					if (itemflag==1) then
						mg2p=mg2p-1
						combovalue=combovalue+1
						if (STstate==0) then
						    coolcount=coolcount+1
						    judgevalue=2.3
						    stvalue=stvalue+1
						    if (stvalue>=maxstvalue) then
						        STstate=2
						        stvalue=maxstvalue
						    end
						elseif (STstate==1) then
						    bestcount=bestcount+1
						    judgevalue=1
						else
						    coolcount=coolcount+1
						    judgevalue=2.3
						end
						ScoreBonus()
						Score()
						Acc()								
					end
					uicombo.Text=combovalue
                    JudgeModule()					
				end
				
				--update acc
				acccount=acccount+lnnotecount[holdmap[source+1]]-lnnotecombo[holdmap[source+1]]
				uiacc.Text=string.format("%.2f",accvalue/acccount)

				--reset pointer
				seg[holdmap[source+1]]=0
				lnnotecount[holdmap[source+1]]=0
				lnnotecombo[holdmap[source+1]]=0
				holdcombovalue[holdmap[source+1]]=1
				dtime[source+1]=holdnote[holdmap[source+1]].time+holdnote[holdmap[source+1]].segments[holdnote[holdmap[source+1]].segments.Length-1].time
				
				
				--update maxsource and holdmap
				--print(source.."-")
				--print(table.concat(holdmap, ", "))
				holdmapinverse[holdmap[source+1]]=0
				holdmap[source+1]=0
				if (source==maxsource and maxsource>0) then
					for j=maxsource,1,-1 do
						if (holdstate[j]~=0 and holdstate[j]~=9999) then
							maxsource=j-1
						break
						end
					end
				end

			end
			
			
			
			
        end
        if (flickstate[source+1]~=0) then
		-- if trying to tap a flick note without flicking, set a miss state
            flickstate[source+1]=0
            flickbreakid[flicknote[source+1].nid]=flicknote[source+1].nid
            flicknote[source+1]=nil
			flickoffset=judgeoffset.g+100
			FlickJudge()
			JudgeModule()
			Combo()
			uicombo.Text=combovalue
			ScoreBonus()
			Score()
			Acc()
        end
    end
end
function Update()
    uptime=Game:Time()
    if (STstate==2) then
        if (STbool=="AutoST") then
            sttime=uptime
            STstate=1
            FeverAnimation(sttime)
            uifevertrack.Alpha=30
            uimaxcombo:SetColor(255,255,0)
        else
            uifeverbutton.Alpha=100
        end
    elseif (STstate==1) then
        stvalue=maxstvalue*(1-(uptime-sttime)/10000)
        if (STbool=="Open2ST" and stvalue<=maxstvalue*0.5 and stvalue>0) then
            STstate2=1
            uifeverbutton.Alpha=100
        elseif (stvalue<=0) then
            stvalue=0
            STstate=0
            STstate2=0
            uifevertrack.Alpha=0
            uifeverbutton.Alpha=0
            uimaxcombo:SetColor(255,255,255)
        end
    end
    uistbar:SetVertex(0,width/2-936+306*stvalue/maxstvalue,70+314*stvalue/maxstvalue)
    uistbar:SetVertex(1,width/2-864+276*stvalue/maxstvalue,70+314*stvalue/maxstvalue)
    uistbar:SetVertex(2,width/2-630,384)
    uistbar:SetVertex(3,width/2-588,384)
    
    if not item0 then
        if itemhold+mg2p+g2p==0 then
            item0=true
            uimaxcombo.Y=-200*sw1610
        end
    end
    if itembool then
        if datamg2p~=mg2p then
            datamg2p=mg2p
            if datamg2p==0 then
                uiItem_mg2p.Alpha=0
                uimg2pvalue.Alpha=0
            else
                uimg2pvalue.Text=datamg2p
                local SItem_eff=Module:Shadow(uiItem_eff,500)
                SItem_eff.X=uiItem_mg2p.X
                SItem_eff:DoAlpha({start=uptime,finish=uptime+500,from=100,to=0})
                SItem_eff:DoRotate({start=uptime,finish=uptime+500,from=360,to=180})
            end
        end
        if datag2p~=g2p then
            datag2p=g2p
            if datag2p==0 then
                uiItem_mg2p.X=uiItem_mg2p.X-100*sw1610
                uimg2pvalue.X=uimg2pvalue.X-100*sw1610
                uiItem_g2p.Alpha=0
                uig2pvalue.Alpha=0
            else
                uig2pvalue.Text=datag2p
                local SItem_eff=Module:Shadow(uiItem_eff,500)
                SItem_eff.X=uiItem_g2p.X
                SItem_eff:DoAlpha({start=uptime,finish=uptime+500,from=100,to=0})
                SItem_eff:DoRotate({start=uptime,finish=uptime+500,from=360,to=180})
            end
        end
        if datam2g~=m2g then
            datam2g=m2g
            if datam2g==0 then
                uiItem_mg2p.X=uiItem_mg2p.X-100*sw1610
                uimg2pvalue.X=uimg2pvalue.X-100*sw1610
                uiItem_g2p.X=uiItem_g2p.X-100*sw1610
                uig2pvalue.X=uig2pvalue.X-100*sw1610
                uiItem_m2g.Alpha=0
                uim2gvalue.Alpha=0
            else
                uim2gvalue.Text=datam2g
                local SItem_eff=Module:Shadow(uiItem_eff,500)
                SItem_eff.X=uiItem_m2g.X
                SItem_eff:DoAlpha({start=uptime,finish=uptime+500,from=100,to=0})
                SItem_eff:DoRotate({start=uptime,finish=uptime+500,from=360,to=180})
            end
        end
        if dataitemhold~=itemhold then
            dataitemhold=itemhold
            if dataitemhold==0 then
                uiItem_mg2p.X=uiItem_mg2p.X-100*sw1610
                uimg2pvalue.X=uimg2pvalue.X-100*sw1610
                uiItem_g2p.X=uiItem_g2p.X-100*sw1610
                uig2pvalue.X=uig2pvalue.X-100*sw1610
                uiItem_m2g.X=uiItem_m2g.X-100*sw1610
                uim2gvalue.X=uim2gvalue.X-100*sw1610
                uiItem_ln.Alpha=0
                uilnvalue.Alpha=0
            else
                uilnvalue.Text=dataitemhold
                local SItem_eff=Module:Shadow(uiItem_eff,500)
                SItem_eff.X=uiItem_ln.X
                SItem_eff:DoAlpha({start=uptime,finish=uptime+500,from=100,to=0,ease=1})
                SItem_eff:DoRotate({start=uptime,finish=uptime+500,from=360,to=180})
            end
        end
    end
    AutoHold()
    for i=1,KNum do
	--track 1-4
        inrtable[i]=math.min(inr[1][i],inr[2][i],inr[3][i],inr[4][i],inr[5][i],inr[6][i],inr[7][i],inr[8][i],inr[9][i],inr[10][i],inr[11][i],inr[12][i],inr[13][i],inr[14][i],inr[15][i],inr[16][i],inr[17][i],inr[18][i],inr[19][i],inr[20][i])
    end
    PressDisplay()
    FinishJudgeCount()
    FinishMaxCombo()
    FinishScore()
    FinishAcc()
    JudgeAni()
    --set pointer at the highest index of source
	local segsource=0
	if (source~=nil) then
        segsource=source
    end
	
	

    for i=1,maxsource+1 do
        if (holdstate[i]~=0 and holdstate[i]~=9999) then
            if (holdnote[i].segments.Length==1 and SegX(holdnote[i])==SegX(holdnote[i],0)) then
            -- single hold LN
				if (uptime>=holdnote[i].time+holdnote[i].segments[0].time) then
				-- if LN is held until the end
                    holdstate[i]=9999
                    holdvalue[holdnote[i].extra]=9999
                    uihitl[i].Alpha=0
                end
            elseif (holdnote[i].segments.Length>1 and SegX(holdnote[i],-2)==SegX(holdnote[i],-1)) then
            -- multi-segment LN ending with a hold
				if (holdstate[i]<=holdnote[i].segments.Length-1) then
                -- if not the last segment
					if (uptime-(holdnote[i].time+holdnote[i].segments[holdstate[i]-1].time)>200*judgescale) then
					-- break if hold for too long time without moving
						if (itemhold>0) then
							itemhold=itemhold-1
							itemholdstate[i]=1
							holdmap[holdmapinverse[i]]=0						
							holdmapinverse[i]=0
						else
							uihitl[i].Alpha=0
							holdstate[i]=0
							holdbreakid[holdnote[i].extra]=holdnote[i].extra								
						end
                    end
                else
				-- set finish state if is held until LN end
                    if (uptime>=holdnote[i].time+holdnote[i].segments[holdnote[i].segments.Length-1].time) then
                        holdstate[i]=9999
                        holdvalue[holdnote[i].extra]=9999
                        uihitl[i].Alpha=0
                    end
                end
            else
			-- single/multi-segment LN ending with a flick
                if (uptime-(holdnote[i].time+holdnote[i].segments[holdstate[i]-1].time)>200*judgescale) then
				-- break if keep holding without moving to the next track
					if (itemhold>0) then
						itemhold=itemhold-1
						itemholdstate[i]=1
						holdmap[holdmapinverse[i]]=0						
						holdmapinverse[i]=0
					else
						uihitl[i].Alpha=0
						holdstate[i]=0
						holdbreakid[holdnote[i].extra]=holdnote[i].extra								
					end
                end
            end
			
			--update when LN ends
			if (holdstate[i]==0 or holdstate[i]==9999) then
				--update combo
				lncombo=0
				while (seg[i]<=holdstatecopy[i]) do
				--calculate combo of previous segments      Fuck **This** Update Function and Refresh Rate!!!!!!!!! 
					combotime=holdnote[i].time+holdnote[i].segments[seg[i]-1].time
					
					if (seg[i]==1) then
						combotime=math.max(combotime,holdnote[i].time)
						lncombo=lncombo+math.floor((combotime+2-holdnote[i].time)*BpmList[1].bpm/15000)-(holdcombovalue[i]-1)
					elseif (seg[i]>1) then
						lncombo=lncombo+math.floor((combotime+2-holdnote[i].time-holdnote[i].segments[seg[i]-2].time)*BpmList[1].bpm/15000)-(holdcombovalue[i]-1)
					end
							
					--reset pointer and counter. If time >= LN endtime, seg[i]=holdstatecopy[i]+1=holdnote[i].segments.Length+1
					--dtime[i]=holdnote[i].time+holdnote[i].segments[seg[i]-1].time
					holdcombovalue[i]=1
					seg[i]=seg[i]+1
				end

				--update HP, score and combo.
				if (lncombo>0) then
					hpvalue=math.min(hpvalue+lncombo,100)
					lnnotecombo[i]=lnnotecombo[i]+lncombo				
					comboevent=1
					judgeevent=1
					ljudgevalue=math.floor(judgevalue)
					judgevalue=1
					bestcount=bestcount+lncombo
					if (STstate==0) then
					    stvalue=stvalue+lncombo*1.2
					    if (stvalue>=maxstvalue) then
					        STstate=2
					        stvalue=maxstvalue
					    end
					end
					for iteration=1,lncombo do
					    combovalue=combovalue+1
						ScoreBonus()
						Score()
						Acc()				    
					end
					uicombo.Text=combovalue
					JudgeModule()
				end
				
				--update acc
				acccount=acccount+lnnotecount[i]-lnnotecombo[i]
				uiacc.Text=string.format("%.2f",accvalue/acccount)

				--reset pointer
				seg[i]=0
				lnnotecount[i]=0
				lnnotecombo[i]=0
				holdcombovalue[i]=1
				dtime[i]=holdnote[i].time+holdnote[i].segments[holdnote[i].segments.Length-1].time
				
				
				
				--update maxsource and holdmap
				--print((holdmap[holdmapinverse[i]]-1).."-")
				--print(table.concat(holdmap, ", "))
				holdmap[holdmapinverse[i]]=0
				holdmapinverse[i]=0
				if (i==maxsource and maxsource>0) then
					for j=maxsource,1,-1 do
						if (holdstate[j]~=0 and holdstate[j]~=9999) then
							maxsource=j-1
						break
						end
					end
				end

			end

			
			
        end
    end
	

    for i=1,segsource+1 do
        if (flickstate[i]==1) then
            if (uptime-flicktime[i]>200*judgescale) then
			--if flick too late, set a miss state
                flickstate[i]=0
                flickbreakid[flicknote[i].nid]=flicknote[i].nid
                flicknote[i]=nil
				
				flickoffset=math.abs(judgeoffset.g)+100
				FlickJudge()
				JudgeModule()
				Combo()
				uicombo.Text=combovalue
				ScoreBonus()
				Score()
				Acc()
            end
        end
    end
    HoldCombo()
    ComboAni()
    PressAni()
    Hp()
end
function OnHit()
    time=Game:Time()
    hitevt=Game:HitEvent()
    noteinfo=hitevt:NoteInfoEx()
    if (noteinfo.y<10) then
        return
    end
    if not (noteinfo.type<=7 and noteinfo.type~=4) then
        return
    end
    AutoNote()
end
function Judge()
    ljudgevalue=math.floor(judgevalue)
    judgeevent=1
    if (STstate==0) then
        if (math.abs(offset)<=judgeoffset.sp) then
            judgevalue=1
            bestcount=bestcount+1
            stvalue=stvalue+1.2
        elseif (math.abs(offset)>judgeoffset.sp and math.abs(offset)<=judgeoffset.p1) then
            judgevalue=2.1
            coolcount=coolcount+1
            stvalue=stvalue+1.15
        elseif (math.abs(offset)>judgeoffset.p1 and math.abs(offset)<=judgeoffset.p2) then
            judgevalue=2.2
            coolcount=coolcount+1
            stvalue=stvalue+1.1
        elseif (math.abs(offset)>judgeoffset.p2 and math.abs(offset)<=judgeoffset.p3) then
            judgevalue=2.3
            coolcount=coolcount+1
            stvalue=stvalue+1
        elseif (math.abs(offset)>judgeoffset.p3 and math.abs(offset)<=judgeoffset.g) then
		    if (g2p>0) then
			    g2p=g2p-1
			    judgevalue=2.3
			    coolcount=coolcount+1
			    stvalue=stvalue+1
		    elseif (mg2p>0) then
			    mg2p=mg2p-1
			    judgevalue=2.3
			    coolcount=coolcount+1	
			    stvalue=stvalue+1
		    else
			    judgevalue=3
			    goodcount=goodcount+1				
			    stvalue=stvalue+0.6
		    end
        else
		    if (mg2p>0) then
			    mg2p=mg2p-1
			    judgevalue=2.3
			    coolcount=coolcount+1
			    stvalue=stvalue+1
		    elseif (m2g>0) then
			    m2g=m2g-1
			
			    if (g2p>0) then
				    g2p=g2p-1
				    judgevalue=2.3
				    coolcount=coolcount+1
				    stvalue=stvalue+1
			    else
			        judgevalue=3
				    goodcount=goodcount+1
				    stvalue=stvalue+0.6
			    end
		    else
			    judgevalue=4
			    misscount=misscount+1
			    stvalue=0
		    end
        end
        if (stvalue>=maxstvalue) then
            stvalue=maxstvalue
            STstate=2
        end
    elseif (STstate==1) then
        if (math.abs(offset)<=judgeoffset.g) then
            judgevalue=1
            bestcount=bestcount+1
        else
            if (m2g>0) then
                m2g=m2g-1
                judgevalue=1
                bestcount=bestcount+1
            elseif (mg2p>0) then
                mg2p=mg2p-1
                judgevalue=1
                bestcount=bestcount+1
            end
        end
    else
        if (math.abs(offset)<=judgeoffset.sp) then
            judgevalue=1
            bestcount=bestcount+1
        elseif (math.abs(offset)>judgeoffset.sp and math.abs(offset)<=judgeoffset.p1) then
            judgevalue=2.1
            coolcount=coolcount+1
        elseif (math.abs(offset)>judgeoffset.p1 and math.abs(offset)<=judgeoffset.p2) then
            judgevalue=2.2
            coolcount=coolcount+1
        elseif (math.abs(offset)>judgeoffset.p2 and math.abs(offset)<=judgeoffset.p3) then
            judgevalue=2.3
            coolcount=coolcount+1
        elseif (math.abs(offset)>judgeoffset.p3 and math.abs(offset)<=judgeoffset.g) then
		    if (g2p>0) then
			    g2p=g2p-1
			    judgevalue=2.3
			    coolcount=coolcount+1
		    elseif (mg2p>0) then
			    mg2p=mg2p-1
			    judgevalue=2.3
			    coolcount=coolcount+1	
		    else
			    judgevalue=3
			    goodcount=goodcount+1
		    end
        else
		    if (mg2p>0) then
			    mg2p=mg2p-1
			    judgevalue=2.3
			    coolcount=coolcount+1
		    elseif (m2g>0) then
			    m2g=m2g-1
			
			    if (g2p>0) then
				    g2p=g2p-1
				    judgevalue=2.3
				    coolcount=coolcount+1
			    else
			        judgevalue=3
				    goodcount=goodcount+1
			    end
		    else
			    judgevalue=4
			    misscount=misscount+1
		    end
        end
    end
    if (judgevalue~=4) then
        hpvalue=hpvalue+1
        if (hpvalue>100) then
            hpvalue=100
        end
    else
        hpvalue=hpvalue-20
        if (hpvalue<0) then
            hpvalue=0
        end
    end
end
function FlickJudge()
    ljudgevalue=math.floor(judgevalue)
    judgeevent=1
    if (STstate==0) then
        if (math.abs(flickoffset)<=judgeoffset.sp) then
            judgevalue=1
            bestcount=bestcount+1
            stvalue=stvalue+1.2
        elseif (math.abs(flickoffset)>judgeoffset.sp and math.abs(flickoffset)<=judgeoffset.p1) then
            judgevalue=2.1
            coolcount=coolcount+1
            stvalue=stvalue+1.15
        elseif (math.abs(flickoffset)>judgeoffset.p1 and math.abs(flickoffset)<=judgeoffset.p2) then
            judgevalue=2.2
            coolcount=coolcount+1
            stvalue=stvalue+1.1
        elseif (math.abs(flickoffset)>judgeoffset.p2 and math.abs(flickoffset)<=judgeoffset.p3) then
            judgevalue=2.3
            coolcount=coolcount+1
            stvalue=stvalue+1
        elseif (math.abs(flickoffset)>judgeoffset.p3 and math.abs(flickoffset)<=judgeoffset.g) then
		    if (g2p>0) then
			    g2p=g2p-1
			    judgevalue=2.3
			    coolcount=coolcount+1
			    stvalue=stvalue+1
		    elseif (mg2p>0) then
			    mg2p=mg2p-1
			    judgevalue=2.3
			    coolcount=coolcount+1	
			    stvalue=stvalue+1
		    else
			    judgevalue=3
			    goodcount=goodcount+1				
			    stvalue=stvalue+0.6
		    end
        else
		    if (mg2p>0) then
			    mg2p=mg2p-1
			    judgevalue=2.3
			    coolcount=coolcount+1
			    stvalue=stvalue+1
		    elseif (m2g>0) then
			    m2g=m2g-1
			
			    if (g2p>0) then
				    g2p=g2p-1
				    judgevalue=2.3
				    coolcount=coolcount+1
				    stvalue=stvalue+1
			    else
			        judgevalue=3
				    goodcount=goodcount+1
				    stvalue=stvalue+0.6
			    end
		    else
			    judgevalue=4
			    misscount=misscount+1
			    stvalue=0
		    end
        end
        if (stvalue>=maxstvalue) then
            stvalue=maxstvalue
            STstate=2
        end
    elseif (STstate==1) then
        if (math.abs(flickoffset)<=judgeoffset.g) then
            judgevalue=1
            bestcount=bestcount+1
        else
            if (m2g>0) then
                m2g=m2g-1
                judgevalue=1
                bestcount=bestcount+1
            elseif (mg2p>0) then
                mg2p=mg2p-1
                judgevalue=1
                bestcount=bestcount+1
            end
        end
    else
        if (math.abs(flickoffset)<=judgeoffset.sp) then
            judgevalue=1
            bestcount=bestcount+1
        elseif (math.abs(flickoffset)>judgeoffset.sp and math.abs(flickoffset)<=judgeoffset.p1) then
            judgevalue=2.1
            coolcount=coolcount+1
        elseif (math.abs(flickoffset)>judgeoffset.p1 and math.abs(flickoffset)<=judgeoffset.p2) then
            judgevalue=2.2
            coolcount=coolcount+1
        elseif (math.abs(flickoffset)>judgeoffset.p2 and math.abs(flickoffset)<=judgeoffset.p3) then
            judgevalue=2.3
            coolcount=coolcount+1
        elseif (math.abs(flickoffset)>judgeoffset.p3 and math.abs(flickoffset)<=judgeoffset.g) then
		    if (g2p>0) then
			    g2p=g2p-1
			    judgevalue=2.3
			    coolcount=coolcount+1
		    elseif (mg2p>0) then
			    mg2p=mg2p-1
			    judgevalue=2.3
			    coolcount=coolcount+1	
		    else
			    judgevalue=3
			    goodcount=goodcount+1
		    end
        else
		    if (mg2p>0) then
			    mg2p=mg2p-1
			    judgevalue=2.3
			    coolcount=coolcount+1
		    elseif (m2g>0) then
			    m2g=m2g-1
			
			    if (g2p>0) then
				    g2p=g2p-1
				    judgevalue=2.3
				    coolcount=coolcount+1
			    else
			        judgevalue=3
				    goodcount=goodcount+1
			    end
		    else
			    judgevalue=4
			    misscount=misscount+1
		    end
        end
    end
    if (judgevalue~=4) then
        hpvalue=hpvalue+1
        if (hpvalue>100) then
            hpvalue=100
        end
    else
        hpvalue=hpvalue-20
        if (hpvalue<0) then
            hpvalue=0
        end
    end
end
function Hp()
--HP Animation, allow HP to change smoothly
    if (datahpvalue>hpvalue*10) then
        datahpvalue=datahpvalue-5
        Play:SetHP(datahpvalue/10)
    elseif (datahpvalue<hpvalue*10) then
        datahpvalue=datahpvalue+5
        Play:SetHP(datahpvalue/10)
    end
end
function JudgeModule()
    if (ljudgevalue==math.floor(judgevalue)) then
        uijudge[math.floor(judgevalue)].Y=260
        if (judgevalue==1) then
            uijudge[5].Y=260
        end
    else
        uijudge[math.floor(ljudgevalue)].Y=5000
        if (ljudgevalue==1) then
            uijudge[5].Y=5000
        end
        uijudge[math.floor(judgevalue)].Y=260
        if (judgevalue==1) then
            uijudge[5].Y=260
        end
    end
end
function JudgeAni()
    if (judgeevent==1) then
        judgeevent=0
        judgeeventtime=uptime
    end
    if (uptime>=judgeeventtime and uptime<=judgeeventtime+100) then
        uijudge[math.floor(judgevalue)].Scale=0.7+0.003*(uptime-judgeeventtime)
        if (judgevalue==1) then
            uijudge[5].Scale=0.7+0.003*(uptime-judgeeventtime)
        end
    end
    if (uptime>=judgeeventtime and uptime<=judgeeventtime+500) then
        if (uijudge[math.floor(judgevalue)].Alpha~=100) then
            uijudge[math.floor(judgevalue)].Alpha=100
        end
    elseif (uptime>judgeeventtime+500 and uptime<=judgeeventtime+850) then
        uijudge[math.floor(judgevalue)].Alpha=100-math.floor((uptime-judgeeventtime-500)/3)
    end
    if (judgevalue==1) then
        if (uptime>=judgeeventtime and uptime<=judgeeventtime+300) then
            if (uijudge[5].Alpha~=100) then
                uijudge[5].Alpha=100
            end
        elseif (uptime>judgeeventtime+300 and uptime<=judgeeventtime+550) then
            uijudge[5].Alpha=100-math.floor((uptime-judgeeventtime-300)/2)
        end
    end
end
function HoldCombo()
    if (autobool==true) then
        holds=20
    else
        holds=maxsource+1
    end
    for i=1,holds do
        if (holdstate[i]~=0 and holdstate[i]~=9999) then

			--update combo
			lncombo=0
			breakflag=0
			while (seg[i]<=holdstate[i] and breakflag==0) do
			--calculate combo of previous segments      Fuck **This** Update Function and Refresh Rate!!!!!!!!! 
				if (uptime>=holdnote[i].time+holdnote[i].segments[seg[i]-1].time) then
					combotime=holdnote[i].time+holdnote[i].segments[seg[i]-1].time
					breakflag=0
				else
					combotime=uptime
					breakflag=1
				end								
				
				if (seg[i]==1) then
				    combotime=math.max(combotime,holdnote[i].time)
					lncombo=lncombo+math.floor((combotime+2-holdnote[i].time)*BpmList[1].bpm/15000)-(holdcombovalue[i]-1)
					if (lncombo>=0 and (breakflag==1 or seg[i]==holdstate[i])) then
					--update holdcombovalue of the current segment
					    holdcombovalue[i]=1+math.floor((combotime+2-holdnote[i].time)*BpmList[1].bpm/15000)
					end
				elseif (seg[i]>1) then
					lncombo=lncombo+math.floor((combotime+2-holdnote[i].time-holdnote[i].segments[seg[i]-2].time)*BpmList[1].bpm/15000)-(holdcombovalue[i]-1)
					if (lncombo>=0 and (breakflag==1 or seg[i]==holdstate[i])) then
					--update holdcombovalue of the current segment
					    holdcombovalue[i]=1+math.floor((combotime+2-holdnote[i].time-holdnote[i].segments[seg[i]-2].time)*BpmList[1].bpm/15000)
					end					
				end
				--break the loop when uptime matches the current LN segment (hold on time)
				if (breakflag==1) then
					break
				end									
				--reset pointer and counter when uptime >= current segment end time and holdstate > current segment
				if (seg[i]<holdstate[i]) then
					dtime[i]=holdnote[i].time+holdnote[i].segments[seg[i]-1].time
					holdcombovalue[i]=1
					seg[i]=seg[i]+1
				else
				    breakflag=1
				end
			end
			
			--update HP, score and combo
			if (lncombo>0) then
			    comboevent=1
				hpvalue=math.min(hpvalue+lncombo,100)			
				lnnotecombo[i]=lnnotecombo[i]+lncombo				
				judgeevent=1
				ljudgevalue=math.floor(judgevalue)
				if (itemholdstate[i]==1) then
				    if (STstate==0) then
					    judgevalue=2.3
					    coolcount=coolcount+lncombo
					    stvalue=stvalue+lncombo
					    if (stvalue>=maxstvalue) then
					        STstate=2
					        stvalue=maxstvalue
					    end
					elseif (STstate==1) then
					    judgevalue=1
					    bestcount=bestcount+lncombo
					else
					    judgevalue=2.3
					    coolcount=coolcount+lncombo
					end
				else
					judgevalue=1
					bestcount=bestcount+lncombo						
					if (STstate==0) then
					    stvalue=stvalue+lncombo*1.2
					    if (stvalue>=maxstvalue) then
					        STstate=2
					        stvalue=maxstvalue
					    end
					end
				end
				JudgeModule()
				for iteration=1,lncombo do
				    combovalue=combovalue+1
					ScoreBonus()
					Score()
					Acc()				    
				end
				uicombo.Text=combovalue				
			end
        end
    end
end
function Combo()
    if (judgevalue~=4) then
        comboevent=1
        combovalue=combovalue+1
    else
        combovalue=0
    end
end
function ComboAni()
    if (comboevent==1) then
        comboevent=0
        comboeventtime=uptime
    end
    if (uptime>=comboeventtime and uptime<=comboeventtime+100) then
        uicombo.Y=130+0.2*(uptime-comboeventtime)
    end
    if (uptime>=comboeventtime and uptime<=comboeventtime+500) then
        if (uicombo.Alpha~=100) then
            uicombo.Alpha=100
        end
    elseif (uptime>comboeventtime+500 and uptime<=comboeventtime+850) then
        uicombo.Alpha=100-math.floor((uptime-comboeventtime-500)/3)
    end
end
function ScoreBonus()
    if (judgevalue~=4) then
	    if (combovalue>20 and combovalue<=50) then
		    scorebonus=0.66
		elseif (combovalue>50 and combovalue<=100) then
		    scorebonus=1.33
		elseif (combovalue>100) then
		    scorebonus=2
		else
		    scorebonus=0
        end
    else
        scorebonus=0
    end
end
function Score()
    if (judgevalue==1) then
        scorevalue=scorevalue+(1+scorebonus)*200
    elseif (judgevalue==2.1) then
        scorevalue=scorevalue+(1+scorebonus)*200*0.915
    elseif (judgevalue==2.2) then
        scorevalue=scorevalue+(1+scorebonus)*200*0.865
    elseif (judgevalue==2.3) then
        scorevalue=scorevalue+(1+scorebonus)*200*0.815
    elseif (judgevalue==3) then
        scorevalue=scorevalue+(1+scorebonus)*200*0.4
    end
	scorevalue=math.floor(scorevalue)
    uiscore.Text=scorevalue
	if (scorevalue>maxscore*0.995) then
	    scorejudge="SSS"
	elseif (scorevalue>maxscore*0.975) then
	    scorejudge="SS"
	elseif (scorevalue>maxscore*0.95) then
	    scorejudge="S"	
	elseif (scorevalue>maxscore*0.9) then
	    scorejudge="A"	
	elseif (scorevalue>maxscore*0.8) then
	    scorejudge="B"
	elseif (scorevalue>maxscore*0.6) then
	    scorejudge="C"
	end
	uimaxcombo.Text=maxcombovalue.."/"..notecount.."\n"..string.format("%.2f",100*scorevalue/maxscore).."%("..scorejudge..")"
end
function Acc()
    acccount=acccount+1
    if (judgevalue==1) then
        accvalue=accvalue+100
    elseif (judgevalue==2.1) then
        accvalue=accvalue+90
    elseif (judgevalue==2.2) then
        accvalue=accvalue+85
    elseif (judgevalue==2.3) then
        accvalue=accvalue+80
    elseif (judgevalue==3) then
        accvalue=accvalue+40
    end
    uiacc.Text=string.format("%.2f",accvalue/acccount)
end
function PressAni()
    for i=1,KNum do
        if (pressupevent[i]==1) then
            pressupevent[i]=0
            pressuptime[i]=uptime
        end
        if (uptime>=pressuptime[i] and uptime<=pressuptime[i]+500 and inrtable[i]==3) then
            uikey[i].Alpha=100-math.floor(0.2*(uptime-pressuptime[i]))
        end
    end
end
function B2L(fun,a,b,c,d)-- 反转字节序
    return fun(d,c,b,a);
end
function ListFun(fun,list,s,e)
    local l = {}
    for i = 1, e-s+1 do
        l[i] = list[e-i+1];
    end
    return fun(table.unpack(l))
end
function Bytes2Float(a,b,c,d)--abcd都是byte
    local S = a >> 7--取[ff]的最高位
    local E = ((a & 127) << 1) + (b >> 7)-- 同理
    local M = ((b & 127) << 16 + c << 8 + d)
    if E == 255 then
        if M == 0 then return 1.8e308;
        else error('Not a Number!');return 0;end
    end

    if E == 0 then return (S == 1 and -1 or 1) * (M / (2^23)) * (2 ^ -126);
    else return (S == 1 and -1 or 1) * (1 + (M / (2^23))) * (2 ^ (E - 127));end
end
function Bytes2Double(a,b,c,d,e,f,g,h)--a~h都是byte
    local S = a >> 7--取[ff]的最高位
    local E = ((a & 127) << 4) + (b >> 4)-- 同理
    local M_org = (((b & 15) << 48) + (c << 40) + (d << 32) + (e << 24) + (f << 16) + (g << 8) + h)
	local M = (M_org / (2^52))
    if E == 255 then
        if M_org == 0 then return 1.8e308;
        else error('Not a Number!');return 0;end
    end

    if E == 0 then return (S == 1 and -1 or 1) * M * (2 ^ -126);
    else return (S == 1 and -1 or 1) * (1 + M) * (2 ^ (E - 1023));end
end
function Bytes2Int(a,b,c,d)
    return (a << 24) + (b << 16) + (c << 8) + d;
end
function ImdBPM(rawImd)
    local beatCount = ListFun(Bytes2Int,rawImd,4,7);
    local tmpBpm = 0
    local bpmCount = 0;
    local I;
    for i = 1, beatCount do
        I = i*12 - 4;
        local bpmValue = ListFun(Bytes2Double,rawImd,I+4,I+11);
        if bpmValue == tmpBpm then goto continue;end
        local bpmTime = ListFun(Bytes2Int,rawImd,I,I+3)
        bpmCount = bpmCount + 1;
        BpmList[bpmCount] = {time=bpmTime,bpm=bpmValue};
        -- print(bpmTime,bpmValue)
        ::continue::
    end
    return I + 12;
    -- bpmtime = ListFun(Bytes2Int,rawImd,8,11)--9~12,example:00 00 00 00
    -- bpmvalue = ListFun(Bytes2Double,rawImd,12,19)--13~20,example:00 00 00 00 00 00 60 40
end

function FinishJudgeCount()
    if (databestcount~=bestcount) then
        databestcount=bestcount
        Play:SetCountByType(1,databestcount)
    end
    if (datacoolcount~=coolcount) then
        datacoolcount=coolcount
        Play:SetCountByType(2,datacoolcount)
    end
    if (datagoodcount~=goodcount) then
        datagoodcount=goodcount
        Play:SetCountByType(3,datagoodcount)
    end
    if (datamisscount~=misscount) then
        datamisscount=misscount
        Play:SetCountByType(4,datamisscount)
    end
end
function FinishMaxCombo()
    if (maxcombovalue<combovalue) then
        maxcombovalue=combovalue
        Play:SetCombo(maxcombovalue)
		uimaxcombo.Text=maxcombovalue.."/"..notecount.."\n"..string.format("%.2f",100*scorevalue/maxscore).."%("..scorejudge..")"
    end
end
function FinishScore()
    if (datascorevalue~=scorevalue) then
        datascorevalue=scorevalue
        Play:SetScore(math.floor(datascorevalue))
    end
end
function FinishAcc()
    if (scorevalue~=0) then
        if (dataacc~=accvalue/acccount) then
            dataacc=accvalue/acccount
            Play:SetAcc(dataacc)
        end
    end
end
function PressDisplay()
    for i=1,KNum do
        if (inrtable[i]==1 and datainrtable[i]~=1) then
            datainrtable[i]=1
            uikey[i].Alpha=70
            uipress[i].Alpha=100
        elseif (inrtable[i]==3 and datainrtable[i]~=3) then
            datainrtable[i]=3
            pressupevent[i]=1
            uipress[i].Alpha=0
		elseif (inrtable[i]==3) then
		    uikey[i].Alpha=0
        end
    end
end
function AutoNote()
    if (autobool==false) then
        if noteinfo.time>=notecurready[noteinfo.x]-200*judgescale then
            notecur[noteinfo.x]=notecur[noteinfo.x]+1
        end
        
	--set miss state if not in auto mode
	
	    --set note break state
		local missflag=0
        if (noteinfo.type==1) then
            hitbreakid[SegX(noteinfo)]=noteinfo.nid
			missflag=1
        elseif (noteinfo.type==5) then
            flickstate[SegX(noteinfo)]=0
            flickbreakid[noteinfo.nid]=noteinfo.nid
			missflag=1
        elseif (noteinfo.type==7 or noteinfo.type==2) then
		    if (itemhold>0) then
			    itemhold=itemhold-1				
				for i=1,20 do
					--find a free "source" to attach to the LN
					if (holdstate[i]==0 or holdstate[i]==9999) then
						itemholdstate[i]=1
						holdstate[i]=1
						holdstatecopy[i]=holdstate[i]
						seg[i]=1
						holdcombovalue[i]=1
						lnnotecount[i]=notecombolist[noteinfo.extra]
						lnnotecombo[i]=0
						if (noteinfo.segments[0].time>0) then
							lnnotecombo[i]=1
							ljudgevalue=math.floor(judgevalue)
							if (STstate==0) then
							    judgevalue=2.3
							    coolcount=coolcount+1
							    stvalue=stvalue+1
							    if (stvalue>=maxstvalue) then
							        STstate=2
							        stvalue=maxstvalue
							    end
							    
							elseif (STstate==1) then
							    judgevalue=1
							    bestcount=bestcount+1
							else
							    judgevalue=2.3
							    coolcount=coolcount+1
							end
							judgeevent=1
							JudgeModule()
							combovalue=combovalue+1
							uicombo.Text=combovalue
							comboevent=1
							ScoreBonus()
							Score()
							Acc()
						end
						holdnoteid[noteinfo.extra]=noteinfo.extra
						holdvalue[noteinfo.extra]=1
						holdnote[i]=noteinfo
						uihitl[i].X=TrackTX(KNum,SegX(holdnote[i]))*gscale
						uihitl[i].Alpha=100
						uihitl[i]:Play()
						if (holdnote[i].segments[0].time~=0) then
							inr[i][SegX(holdnote[i])]=1
						end
						if (maxsource+1<i) then
						    maxsource=i-1
						end
						break
					end
				end				
			
			
			else
			    missflag=1
				holdbreakid[noteinfo.extra]=noteinfo.extra
				--calculate missed combo
				acccount=acccount+notecombolist[noteinfo.extra]-1			
			end

        end
		--set miss state and update acc
		if (missflag==1) then
			offset=math.abs(judgeoffset.g)+100
			if not (STstate==1 and mg2p==0 and m2g==0) then
			    Judge()
			    JudgeModule()
			    Combo()
			    uicombo.Text=combovalue
			    ScoreBonus()
			    Score()
			    Acc()			
			else
			    acccount=acccount+1
			    uiacc.Text=string.format("%.2f",accvalue/acccount)
			end
		end
	
		
    else
	--auto mode
        if not (noteinfo.type==7 and noteinfo.segments[0].time==0) then
		--if not a LN starts with a flick (tap/flick/LN starting with a hold), add notehead combo
            ljudgevalue=math.floor(judgevalue)
            judgevalue=1
            judgeevent=1
            JudgeModule()
            bestcount=bestcount+1
            combovalue=combovalue+1
            uicombo.Text=combovalue
            comboevent=1
			ScoreBonus()
            Score()
            Acc()
            if (STstate==0) then
                stvalue=stvalue+1.2
                if (stvalue>=maxstvalue) then
                    STstate=2
                    stvalue=maxstvalue
                end
            end
        else
            AutoPressAni(SegX(noteinfo),time)
        end
        if (noteinfo.type==1) then
		--tap
            hitnoteid[SegX(noteinfo)]=noteinfo.nid
            uihit[SegX(noteinfo)]:Play()
            AutoPressAni(SegX(noteinfo),time)
            if Audiobool then Audio:Play(Tap_hitsound,Value_hitsound);end
        elseif (noteinfo.type==5) then
		--flick
            flicknoteid[noteinfo.nid]=noteinfo.nid
            AutoPressAni(SegX(noteinfo),time)
	        uihit[SegX(noteinfo)+noteinfo.arrow-10]:Play()
            AutoPressAni(SegX(noteinfo)+noteinfo.arrow-10,time)
            if Audiobool then Audio:Play(Flick_hitsound,Value_hitsound);end
        else
            if Audiobool then Audio:Play(Tap_hitsound,Value_hitsound);end
		--LN
            holdnoteid[noteinfo.extra]=noteinfo.extra
            for i=1,20 do
			    --find a free "source" to attach to the LN
                if (holdstate[i]==0 or holdstate[i]==9999) then
                    holdstate[i]=1
					holdstatecopy[i]=holdstate[i]
					seg[i]=1
					holdcombovalue[i]=1
					lnnotecount[i]=notecombolist[noteinfo.extra]
					lnnotecombo[i]=0
					if (noteinfo.segments[0].time>0) then
					    lnnotecombo[i]=1
					end
                    holdvalue[noteinfo.extra]=1
                    holdnote[i]=noteinfo
                    uihitl[i].X=TrackTX(KNum,SegX(holdnote[i]))*gscale
                    uihitl[i].Alpha=100
                    uihitl[i]:Play()
                    if (holdnote[i].segments[0].time~=0) then
                        inr[i][SegX(holdnote[i])]=1
                    end
                    break
                end
            end
        end
    end
end
function AutoHold()
    for i=1,20 do
        if (autobool==true or itemholdstate[i]==1) then
            if (holdstate[i]~=0 and holdstate[i]~=9999) then
			--if LN is in progress
                while (holdstate[i]~=9999 and uptime>=holdnote[i].time+holdnote[i].segments[holdstate[i]-1].time) do
				--when time >= current segment end time, set pointer to the next segment
                    holdstate[i]=holdstate[i]+1
                    uihitl[i].X=TrackTX(KNum,SegX(holdnote[i],holdstate[i]-2))*gscale
                    holdvalue[holdnote[i].extra]=holdstate[i]
                    if (holdstate[i]<=holdnote[i].segments.Length) then
					--call a comboevent if not at LN tail
					    holdstatecopy[i]=holdstate[i]
						lnnotecombo[i]=lnnotecombo[i]+1
                        ljudgevalue=math.floor(judgevalue)

						if (itemholdstate[i]==1) then
						    if (STstate==1) then
						        judgevalue=1
							    bestcount=bestcount+1		
							elseif (STstate==0) then
						        judgevalue=2.3
							    coolcount=coolcount+1
							    stvalue=stvalue+1
							    if (stvalue>=maxstvalue) then
							        stvalue=maxstvalue
							        STstate=2
							    end
							else
							    judgevalue=2.3
							    coolcount=coolcount+1
							end
						else
						    if Audiobool then Audio:Play(Drag_hitsound,Value_hitsound);end
							judgevalue=1
							bestcount=bestcount+1					
							if (STstate==0) then
							    stvalue=stvalue+1.2
							    if (stvalue>=maxstvalue) then
							        STstate=2
							        stvalue=maxstvalue
							    end
							end
						end
                        judgeevent=1
                        JudgeModule()
                        combovalue=combovalue+1
                        comboevent=1
                        uicombo.Text=combovalue
                        ScoreBonus()
						Score()
                        Acc()
                        inr[i][SegX(holdnote[i],holdstate[i]-2)]=1
                        if (holdstate[i]<=2) then
                            inr[i][SegX(holdnote[i])]=3
                        else
                            inr[i][SegX(holdnote[i],holdstate[i]-3)]=3
                        end
                    else
					--set finish state at LN tail
                        holdstate[i]=9999
                        holdvalue[holdnote[i].extra]=9999
                        uihitl[i].Alpha=0
                        if (holdnote[i].segments.Length==1) then
                            inr[i][SegX(holdnote[i])]=3
                        else
                            inr[i][SegX(holdnote[i],-2)]=3
                        end
                        if not ((holdnote[i].segments.Length==1 and SegX(holdnote[i])==SegX(holdnote[i],0)) or (holdnote[i].segments.Length>1 and SegX(holdnote[i],-2)==SegX(holdnote[i],-1))) then
                            uihit[SegX(holdnote[i],-1)]:Play()
                            comboevent=1
                            combovalue=combovalue+1
							lnnotecombo[i]=lnnotecombo[i]+1
                            uicombo.Text=combovalue
                            ljudgevalue=math.floor(judgevalue)
							if (itemholdstate[i]==1) then
							    if (STstate==1) then
							        judgevalue=1
							        bestcount=bestcount+1		
							    elseif (STstate==0) then
								    judgevalue=2.3
								    coolcount=coolcount+1
								    stvalue=stvalue+1
								    if (stvalue>=maxstvalue) then
								        STstate=2
								        stvalue=maxstvalue
								    end
								else
								    judgevalue=2.3
								    coolcount=coolcount+1
								end
							else
							    if Audiobool then Audio:Play(Flick_hitsound,Value_hitsound);end
								judgevalue=1
								bestcount=bestcount+1						
								if (STstate==0) then
							        stvalue=stvalue+1.2
							        if (stvalue>=maxstvalue) then
							            STstate=2
							            stvalue=maxstvalue
							        end
							    end
							end
                            judgeevent=1
                            JudgeModule()
							ScoreBonus()
                            Score()
                            Acc()
                            AutoPressAni(SegX(holdnote[i],-1),uptime)
                        end
						
					    --update combo when LN is finished
						lncombo=0
						while (seg[i]<=holdstatecopy[i]) do
						--calculate combo of previous segments      Fuck **This** Update Function and Refresh Rate!!!!!!!!! 
							if (uptime>=holdnote[i].time+holdnote[i].segments[seg[i]-1].time) then
								combotime=holdnote[i].time+holdnote[i].segments[seg[i]-1].time
								breakflag=0
							else
								combotime=uptime
								breakflag=1
							end								
							
							if (seg[i]==1) then
								combotime=math.max(combotime,holdnote[i].time)
								lncombo=lncombo+math.floor((combotime+2-holdnote[i].time)*BpmList[1].bpm/15000)-(holdcombovalue[i]-1)
							elseif (seg[i]>1) then
								lncombo=lncombo+math.floor((combotime+2-holdnote[i].time-holdnote[i].segments[seg[i]-2].time)*BpmList[1].bpm/15000)-(holdcombovalue[i]-1)
							end
							--break the loop when finish
							if (breakflag==1) then
								break
							end									
							--reset pointer and counter. If time >= LN endtime, seg[i]=holdstatecopy[i]+1=holdnote[i].segments.Length+1
							--dtime[i]=holdnote[i].time+holdnote[i].segments[seg[i]-1].time
							holdcombovalue[i]=1
							seg[i]=seg[i]+1
						end


						
						--update HP, score and combo.
						if (lncombo>0) then
							hpvalue=math.min(hpvalue+lncombo,100)
							lnnotecombo[i]=lnnotecombo[i]+lncombo				
							comboevent=1
							judgeevent=1
							ljudgevalue=math.floor(judgevalue)
							if (itemholdstate[i]==1) then
							    if (STstate==1) then
							        judgevalue=1
							        bestcount=bestcount+lncombo
							    elseif (STstate==0) then
								    judgevalue=2.3
								    coolcount=coolcount+lncombo
								    stvalue=stvalue+lncombo
								    if (stvalue>=maxstvalue) then
								        STstate=2
								        stvalue=maxstvalue
								    end
								else
								    judgevalue=2.3
								    coolcount=coolcount+lncombo
								end
							else
								judgevalue=1
								bestcount=bestcount+lncombo				
								if (STstate==0) then
							        stvalue=stvalue+1.2*lncombo
							        if (stvalue>=maxstvalue) then
							            STstate=2
							            stvalue=maxstvalue
							        end
							    end		
							end
							for iteration=1,lncombo do
							    combovalue=combovalue+1
								ScoreBonus()
								Score()
								Acc()				    
							end
							uicombo.Text=combovalue
							JudgeModule()
						end
						
						--update acc
						acccount=acccount+lnnotecount[i]-lnnotecombo[i]
						uiacc.Text=string.format("%.2f",accvalue/acccount)

						--reset pointer
						seg[i]=0
						lnnotecount[i]=0
						lnnotecombo[i]=0
						holdcombovalue[i]=1
						dtime[i]=holdnote[i].time+holdnote[i].segments[holdnote[i].segments.Length-1].time
						itemholdstate[i]=0
						if (i==maxsource and maxsource>0) then
							for j=maxsource,1,-1 do
								if (holdstate[j]~=0 and holdstate[j]~=9999) then
									maxsource=j-1
								break
								end
							end
						end
                    end
                end
            end
        end
    end
end
function AutoPressAni(pos,presstime)
    pressupevent[pos]=1
    uipress[pos]:DoAlpha({start=presstime,finish=presstime+50,from=100,to=0,ease=1})
end

function Diff(version)
    if (string.find(version,"ez") or string.find(version,"easy")) then
        DiffName="ez"
    elseif (string.find(version,"nm") or string.find(version,"normal")) then
        DiffName="nm"
    elseif (string.find(version,"hd") or string.find(version,"hard")) then
        DiffName="hd"
    elseif (string.find(version,"mx") or string.find(version,"crazy")) then
        DiffName="mx"
    elseif (string.find(version,"sp") or string.find(version,"super")) then
        DiffName="sp"
    end
    if (string.find(version,"4k")) then
        KNum=4
    elseif (string.find(version,"5k")) then
        KNum=5
    elseif (string.find(version,"6k")) then
        KNum=6
    end
end
function SegX(note,pos)
    if (pos==nil) then
        return (note.x)
    elseif (pos>=0) then
        return (note.segments[pos].x)
    else
        return (note.segments[note.segments.Length+pos].x)
    end
end
function TrackTX(KNum,i)
    return (-840+840/KNum+1680/KNum*(i-1))
end
function FeverAnimation(ftime)
    uifeverv:DoMoveY({from=-957*trackscale,to=78*trackscale,start=ftime,finish=ftime+500})
    uifeverv:DoScale({from=6.35,to=0.4,start=ftime,finish=ftime+500},{from=6.35,to=0.4,start=ftime,finish=ftime+500})
end
function JsonFind(rawJson)
--查找bpm
    jsonbpm=tonumber(string.match(rawJson,'"tempo": ([%d.]+)'))
    BpmList={[1]={time=0,bpm=jsonbpm}}
    audioTime=0
    KNum=0
--查找"track":字符串数量来确定轨道数量
    for jsontrack in string.gmatch(rawJson,'"track": [3-8]') do
        KNum=KNum+1
    end
--对字符串进行轨道分割，分割标记位置
    local trackmark={}
    for i=1,KNum+1 do
        trackmark[i],_=string.find(rawJson,'"track": '..i+2)
    end
    jsontable={{},{},{},{},{},{}}
--分割字符串，并提取相应数据到表
    for i=1,KNum do
        local tempstring=string.sub(rawJson,trackmark[i],trackmark[i+1])
        local tablecur=1
        for v in string.gmatch(tempstring,'"tick": (%d+)') do
            audioTime=math.max(audioTime,tonumber(v))
            jsontable[i][tablecur]={track=i+2,tick=tonumber(v)}
            tablecur=tablecur+1
        end
        
        tablecur=1
        for v in string.gmatch(tempstring,'"dur": (%d+)') do
            jsontable[i][tablecur].dur=tonumber(v)
            tablecur=tablecur+1
        end
        
        tablecur=1
        for v in string.gmatch(tempstring,'"dur": (%d+)') do
            jsontable[i][tablecur].dur=tonumber(v)
            tablecur=tablecur+1
        end
        
        tablecur=1
        for v in string.gmatch(tempstring,'"isEnd": (%d+)') do
            jsontable[i][tablecur].isend=tonumber(v)
            tablecur=tablecur+1
        end
        
        tablecur=1
        for v in string.gmatch(tempstring,'"toTrack": (%d+)') do
            jsontable[i][tablecur].totrack=tonumber(v)
            tablecur=tablecur+1
        end
        
        tablecur=1
        for v in string.gmatch(tempstring,'"attr": (%d+)') do
            jsontable[i][tablecur].attr=tonumber(v)
            tablecur=tablecur+1
        end
    end
    audioTime=math.floor(audioTime*1250/jsonbpm+0.5)
end
function JsonNote()
    local nidx=math.max(#jsontable[1],#jsontable[2],#jsontable[3],#jsontable[4],#jsontable[5],#jsontable[6])
    notecount=0
    for i=1,KNum do
        for k=1,#jsontable[i] do
            if jsontable[i][k].attr==0 then
                notecount=notecount+1
                local jsonnote={
                    type=1,
                    nid=(i-1)*nidx+k,
                    extra=(i-1)*nidx+k,
                    time=math.floor(jsontable[i][k].tick*1250/jsonbpm+0.5),
                    x=i,
                    y=10,
                }
                Note:AddVirtual(jsonnote)
            elseif jsontable[i][k].attr==3 then
                if jsontable[i][k].isend==1 then
                    if jsontable[i][k].totrack==i+2 then
                        local jsonnote={
                            type=2,
                            nid=(i-1)*nidx+k,
                            extra=(i-1)*nidx+k,
                            time=math.floor(jsontable[i][k].tick*1250/jsonbpm+0.5),
                            endtime=math.floor((jsontable[i][k].tick+jsontable[i][k].dur)*1250/jsonbpm+0.5),
                            x=i,
                            y=10,
                        }
                        jsonnote.segments={[1]={x=i,time=jsonnote.endtime-jsonnote.time}}
                        notecount=notecount+jsontable[i][k].dur//12+1
                        notecombolist[jsonnote.extra]=jsontable[i][k].dur//12+1
                        Note:AddVirtual(jsonnote)
                    else
                        notecount=notecount+1
                        local jsonnote={
                            type=5,
                            nid=(i-1)*nidx+k,
                            extra=(i-1)*nidx+k,
                            time=math.floor(jsontable[i][k].tick*1250/jsonbpm+0.5),
                            x=i,
                            y=10,
                            arrow=jsontable[i][k].totrack-i+8,
                        }
                        Note:AddVirtual(jsonnote)
                    end
                else
                    local snotetable={}
                    local snoteend=false
                    table.insert(snotetable,jsontable[i][k])
                    while snoteend==false do
                        snoteend=true
                        if snotetable[#snotetable].track==snotetable[#snotetable].totrack then
                            local nextpart=snotetable[#snotetable].track-2
                            for m=1,#jsontable[nextpart] do
                                if jsontable[nextpart][m].tick==snotetable[#snotetable].tick+snotetable[#snotetable].dur and jsontable[nextpart][m].attr==4 and jsontable[nextpart][m].track~=jsontable[nextpart][m].totrack then
                                    table.insert(snotetable,jsontable[nextpart][m])
                                    snoteend=false
                                    goto jsonskip1
                                end
                            end
                            ::jsonskip1::
                            if snotetable[#snotetable].isend==1 then
                                snoteend=true
                            end
                        else
                            local nextpart=snotetable[#snotetable].totrack-2
                            for m=1,#jsontable[nextpart] do
                                if jsontable[nextpart][m].tick==snotetable[#snotetable].tick and jsontable[nextpart][m].attr==4 and jsontable[nextpart][m].track==jsontable[nextpart][m].totrack then
                                    table.insert(snotetable,jsontable[nextpart][m])
                                    snoteend=false
                                    goto jsonskip2
                                end
                            end
                            ::jsonskip2::
                            if snotetable[#snotetable].isend==1 then
                                snoteend=true
                            end
                        end
                    end
                    local jsonnote={
                        type=7,
                        nid=(i-1)*nidx+k,
                        extra=(i-1)*nidx+k,
                        time=math.floor(snotetable[1].tick*1250/jsonbpm+0.5),
                        endtime=math.floor((snotetable[#snotetable].tick+snotetable[#snotetable].dur)*1250/jsonbpm+0.5),
                        x=i,
                        y=10,
                        segments={}
                    }
                    notecombolist[jsonnote.extra]=0
                    if snotetable[1].totrack~=i+2 then
                        jsonnote.segments[1]={time=0,x=snotetable[1].totrack-2}
                        local jsonsnote={
                            type=10,
                            nid=jsonnote.nid,
                            extra=jsonnote.extra,
                            time=jsonnote.time,
                            x=i,
                            y=101,
                            arrow=snotetable[1].totrack+8-i,
                        }
                        Note:AddVirtual(jsonsnote)
                        if #snotetable==2 then
                            jsonnote.segments[2]={time=jsonnote.endtime-jsonnote.time,x=snotetable[1].totrack-2}
                            notecombolist[jsonnote.extra]=notecombolist[jsonnote.extra]+snotetable[2].dur//12+1
                            notecount=notecount+snotetable[2].dur//12+1
                            local jsonsnote={
                                type=31,
                                nid=jsonnote.nid,
                                extra=jsonnote.extra,
                                time=jsonnote.time,
                                endtime=jsonnote.endtime,
                                x=snotetable[1].totrack-2,
                                y=102,
                            }
                            Note:AddVirtual(jsonsnote)
                        else
                            if #snotetable%2==0 then
                                for m=2,#snotetable//2 do
                                    jsonnote.segments[m]={time=math.floor(snotetable[2*m-1].tick*1250/jsonbpm+0.5)-jsonnote.time,x=snotetable[2*m-1].totrack-2}
                                    notecombolist[jsonnote.extra]=notecombolist[jsonnote.extra]+snotetable[2*m-2].dur//12+1
                                    notecount=notecount+snotetable[2*m-2].dur//12+1
                                    local jsonsnote={
                                        type=12,
                                        nid=jsonnote.nid,
                                        extra=jsonnote.extra,
                                        time=math.floor(snotetable[2*m-2].tick*1250/jsonbpm+0.5),
                                        endtime=math.floor(snotetable[2*m-1].tick*1250/jsonbpm+0.5),
                                        x=snotetable[2*m-2].track-2,
                                        y=100+m,
                                        arrow=snotetable[2*m-1].totrack-snotetable[2*m-1].track+10,
                                    }
                                    jsonsnote.segments={[1]={time=jsonsnote.endtime-jsonsnote.time,x=jsonsnote.x}}
                                    if jsonsnote.y==102 then
                                        jsonsnote.type=11
                                    end
                                    Note:AddVirtual(jsonsnote)
                                end
                                jsonnote.segments[#snotetable//2+1]={time=jsonnote.endtime-jsonnote.time,x=snotetable[#snotetable].totrack-2}
                                notecombolist[jsonnote.extra]=notecombolist[jsonnote.extra]+snotetable[#snotetable].dur//12+1
                                notecount=notecount+snotetable[#snotetable].dur//12+1
                                local jsonsnote={
                                    type=32,
                                    nid=jsonnote.nid,
                                    extra=jsonnote.extra,
                                    time=math.floor(snotetable[#snotetable].tick*1250/jsonbpm+0.5),
                                    endtime=jsonnote.endtime,
                                    x=snotetable[#snotetable].track-2,
                                    y=100+#snotetable//2+1,
                                }
                                Note:AddVirtual(jsonsnote)
                            else
                                notecombolist[jsonnote.extra]=notecombolist[jsonnote.extra]+1
                                notecount=notecount+1
                                for m=2,#snotetable//2+1 do
                                    jsonnote.segments[m]={time=math.floor(snotetable[2*m-1].tick*1250/jsonbpm+0.5)-jsonnote.time,x=snotetable[2*m-1].totrack-2}
                                    notecombolist[jsonnote.extra]=notecombolist[jsonnote.extra]+snotetable[2*m-2].dur//12+1
                                    notecount=notecount+snotetable[2*m-2].dur//12+1
                                    local jsonsnote={
                                        type=12,
                                        nid=jsonnote.nid,
                                        extra=jsonnote.extra,
                                        time=math.floor(snotetable[2*m-2].tick*1250/jsonbpm+0.5),
                                        endtime=math.floor(snotetable[2*m-1].tick*1250/jsonbpm+0.5),
                                        x=snotetable[2*m-2].track-2,
                                        y=100+m,
                                        arrow=snotetable[2*m-1].totrack-snotetable[2*m-1].track+10,
                                    }
                                    jsonsnote.segments={[1]={time=jsonsnote.endtime-jsonsnote.time,x=jsonsnote.x}}
                                    if jsonsnote.y==102 then
                                        jsonsnote.type=jsonsnote.type-1
                                    end
                                    if jsonsnote.y==100+#snotetable//2+1 then
                                        jsonsnote.type=jsonsnote.type+10
                                    end
                                    Note:AddVirtual(jsonsnote)
                                end
                            end
                        end
                    else
                        if #snotetable%2==1 then
                            for m=1,#snotetable//2 do
                                jsonnote.segments[m]={time=math.floor(snotetable[2*m].tick*1250/jsonbpm+0.5)-jsonnote.time,x=snotetable[2*m].totrack-2}
                                notecombolist[jsonnote.extra]=notecombolist[jsonnote.extra]+snotetable[2*m-1].dur//12+1
                                notecount=notecount+snotetable[2*m-1].dur//12+1
                                local jsonsnote={
                                    type=12,
                                    nid=jsonnote.nod,
                                    extra=jsonnote.extra,
                                    time=math.floor(snotetable[2*m-1].tick*1250/jsonbpm+0.5),
                                    endtime=math.floor(snotetable[2*m].tick*1250/jsonbpm+0.5),
                                    x=snotetable[2*m-1].track-2,
                                    y=100+m,
                                    arrow=snotetable[2*m].totrack-snotetable[2*m].track+10,
                                }
                                jsonsnote.segments={[1]={time=jsonsnote.endtime-jsonsnote.time,x=jsonsnote.x}}
                                Note:AddVirtual(jsonsnote)
                            end
                            jsonnote.segments[#snotetable//2+1]={time=jsonnote.endtime-jsonnote.time,x=snotetable[#snotetable].totrack-2}
                            notecombolist[jsonnote.extra]=notecombolist[jsonnote.extra]+snotetable[#snotetable].dur//12+1
                            notecount=notecount+snotetable[#snotetable].dur//12+1
                            local jsonsnote={
                                type=32,
                                nid=jsonnote.nid,
                                extra=jsonnote.extra,
                                time=math.floor(snotetable[#snotetable].tick*1250/jsonbpm+0.5),
                                endtime=jsonnote.endtime,
                                x=snotetable[#snotetable].track-2,
                                y=100+#snotetable//2+1,
                            }
                            Note:AddVirtual(jsonsnote)
                        else
                            notecombolist[jsonnote.extra]=notecombolist[jsonnote.extra]+1
                            notecount=notecount+1
                            for m=1,#snotetable//2 do
                                jsonnote.segments[m]={time=math.floor(snotetable[2*m].tick*1250/jsonbpm+0.5)-jsonnote.time,x=snotetable[2*m].totrack-2}
                                notecombolist[jsonnote.extra]=notecombolist[jsonnote.extra]+snotetable[2*m-1].dur//12+1
                                notecount=notecount+snotetable[2*m-1].dur//12+1
                                local jsonsnote={
                                    type=12,
                                    nid=jsonnote.nid,
                                    extra=jsonnote.extra,
                                    time=math.floor(snotetable[2*m-1].tick*1250/jsonbpm+0.5),
                                    endtime=math.floor(snotetable[2*m].tick*1250/jsonbpm+0.5),
                                    x=snotetable[2*m-1].track-2,
                                    y=100+m,
                                    arrow=snotetable[2*m].totrack-snotetable[2*m].track+10,
                                }
                                jsonsnote.segments={[1]={time=jsonsnote.endtime-jsonsnote.time,x=jsonsnote.x}}
                                if jsonsnote.y==100+#snotetable//2 then
                                    jsonsnote.type=22
                                end
                                Note:AddVirtual(jsonsnote)
                            end
                        end
                    end
                    Note:AddVirtual(jsonnote)
                end
            end
        end
    end
end
function Rmp2Json()
end
                    