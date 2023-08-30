class DagViewer {

    initialize() {

      // Since 2.2 you can also author concise templates with method chaining instead of GraphObject.make
      // For details, see https://gojs.net/latest/intro/buildingObjects.html
      const $ = go.GraphObject.make;  // for conciseness in defining templates

      // some constants that will be reused within templates
      var roundedRectangleParams = {
        parameter1: 2,  // set the rounded corner
        spot1: go.Spot.TopLeft, spot2: go.Spot.BottomRight  // make content go all the way to inside edges of rounded corners
      };

      myDiagram =
        $(go.Diagram, "myDiagramDiv",  // must name or refer to the DIV HTML element
          {
            "animationManager.initialAnimationStyle": go.AnimationManager.None,
            "InitialAnimationStarting": e => {
                var animation = e.subject.defaultAnimation;
                animation.easing = go.Animation.EaseOutExpo;
                animation.duration = 900;
                animation.add(e.diagram, 'scale', 0.1, 1);
                animation.add(e.diagram, 'opacity', 0, 1);
            },

            // have mouse wheel events zoom in and out instead of scroll up and down
            "toolManager.mouseWheelBehavior": go.ToolManager.WheelZoom,
            "toolManager.hoverDelay": 1,
            "toolManager.toolTipDuration": 60000,
            // support double-click in background creating a new node
            // "clickCreatingTool.archetypeNodeData": { text: "new node" },
            // enable undo & redo
            "undoManager.isEnabled": true,
            positionComputation: function (diagram, pt) {
              return new go.Point(Math.floor(pt.x), Math.floor(pt.y));
            }
          });

      // define the Node template
      myDiagram.nodeTemplate =
        $(go.Node, "Auto",
          {
            locationSpot: go.Spot.Top,
            isShadowed: true, shadowBlur: 1,
            shadowOffset: new go.Point(0, 1),
            shadowColor: "rgba(0, 0, 0, .14)",
            selectionChanged: onNodeSelected,
            toolTip:                       // define a tooltip for each node
            $("ToolTip",
                $(go.TextBlock, { margin: 4 },
                  // use a converter to display information about the diagram model
                  new go.Binding("text", "id", getNodeTooltipText))
             )
          },
          new go.Binding("location", "loc", go.Point.parse).makeTwoWay(go.Point.stringify),
          // define the node's outer shape, which will surround the TextBlock
          $(go.Shape, "RoundedRectangle", roundedRectangleParams,
            {
              name: "SHAPE", fill: "#ffffff", strokeWidth: 0,
              stroke: null,
              portId: "",  // this Shape is the Node's port, not the whole Node
              fromLinkable: true, fromLinkableSelfNode: true, fromLinkableDuplicates: true,
              toLinkable: true, toLinkableSelfNode: true, toLinkableDuplicates: true,
              cursor: "pointer"
            }, new go.Binding("fill", "color")),
          $(go.TextBlock,
            {
              font: "bold small-caps 11pt helvetica, bold arial, sans-serif", margin: 7, stroke: "rgba(0, 0, 0, .87)",
              editable: false  // editing the text automatically updates the model data
            },
            new go.Binding("text").makeTwoWay())
        );

      let alignOption = go.LayeredDigraphLayout.AlignUpperLeft | go.LayeredDigraphLayout.AlignUpperRight |
        go.LayeredDigraphLayout.AlignLowerLeft | go.LayeredDigraphLayout.AlignLowerRight;
      myDiagram.layout = $(go.LayeredDigraphLayout, {setsPortSpots: true,
       aggressiveOption: go.LayeredDigraphLayout.AggressiveMore,
       layeringOption: go.LayeredDigraphLayout.LayerOptimalLinkLength,
       cycleRemoveOption: go.LayeredDigraphLayout.CycleDepthGreedy,
       direction: 0});

       myDiagram.groupTemplate =
          $(go.Group, "Vertical",
            $(go.Panel, "Auto",
              $(go.Shape, "RoundedRectangle",  // surrounds the Placeholder
                { parameter1: 14,
                  fill: "rgba(128,128,128,0.33)" }),
              $(go.Placeholder,    // represents the area of all member parts,
                { padding: 5})  // with some extra padding around them
            ),
            $(go.TextBlock,         // group title
              { alignment: go.Spot.TopLeft, font: "bold small-caps 11pt helvetica, bold arial, sans-serif" },
              new go.Binding("text", "text"))
          );
       myDiagram.groupTemplate.layout = $(go.LayeredDigraphLayout,
        { columnSpacing: 30, setsPortSpots: true,
           aggressiveOption: go.LayeredDigraphLayout.AggressiveMore,
           layeringOption: go.LayeredDigraphLayout.LayerOptimalLinkLength,
           cycleRemoveOption: go.LayeredDigraphLayout.CycleDepthGreedy,
           direction: 0});

      // replace the default Link template in the linkTemplateMap
      myDiagram.linkTemplate =
        $(go.Link,  // the whole link panel
          {
            curve: go.Link.Bezier,
            reshapable: true,
            toShortLength: 3,
            selectionChanged: onLinkSelected,
            toolTip:
            $("ToolTip",
                $(go.TextBlock, { margin: 4 },
                  // use a converter to display information about the diagram model
                  new go.Binding("text", "", function(s){ return getLinkTooltipText(s); }))
             )
          },
          new go.Binding("points").makeTwoWay(),
          new go.Binding("curviness"),
          $(go.Shape,  // the link shape
            { strokeWidth: 1.5 },
            new go.Binding('stroke', 'progress', progress => progress ? "#52ce60" /* green */ : 'black'),
            new go.Binding('strokeWidth', 'progress', progress => progress ? 2.5 : 1.5)),
          $(go.Shape,  // the arrowhead
            { toArrow: "standard", stroke: null },
            new go.Binding('fill', 'progress', progress => progress ? "#52ce60" /* green */ : 'black')),
          $(go.Panel, "Auto",
            $(go.Shape,  // the label background, which becomes transparent around the edges
              {
                fill: $(go.Brush, "Radial",
                  { 0: "rgb(245, 245, 245)", 0.7: "rgb(245, 245, 245)", 1: "rgba(245, 245, 245, 0)" }),
                stroke: null
              }),
            $(go.TextBlock, "transition",  // the label text
              {
                textAlign: "center",
                visible: false,
                font: "9pt helvetica, arial, sans-serif",
                overflow: go.TextBlock.OverflowEllipsis,
                width: 150,
                maxLines: 1,
                margin: 4,
                editable: false
              },
              // editing the text automatically updates the model data
              new go.Binding("text").makeTwoWay())
          )
        );

      // read in the JSON data from the "mySavedModel" element
      this.load();
    }

    load(selectedStates) {
        let self = this;

        if (pipelineData != null) {
            self.redraw(pipelineData, selectedStates);
        } else {
            $(".overlay").show();
            // Always obtain fresh JSON data from external file when reloading the diagram since the model variable itself
            // becomes dirty as GoJS adds more UI information on it
            $.get("/capex/browseDir", {dir: $("#input-path").val()}, function(data){
                data.moduleNodes.forEach(n => {
                    n.name = n.text;
                    n.initials = n.text.replace(/[^A-Z0-9_]+/g, "");
                });
                data.moduleNodeGroups = self.generateGroups(data.moduleNodes);
                $(".overlay").hide();
                pipelineData = data;
                self.redraw(data, selectedStates);

            }).fail(function (xhr, status, error) {
                  $(".overlay").hide();
                  alert(error + "\n" + xhr.responseText);
              });
        }
    }

    getVisibleNodes(data, selectedStates) {
        let self = this;
        var nodeArray = (selectedStates == null ? data.moduleNodes :
            data.moduleNodes.filter(m => selectedStates.includes(m.color)));
        if ($("#profile").val() != "none") {
            let nodeIdsInProfile = data.profiles[$("#profile").val()];
            nodeArray = nodeArray.filter(m => nodeIdsInProfile.includes(m.id));
        }
        var urlParams = new URLSearchParams(window.location.search);
        if(urlParams.has('startNode') || urlParams.has('endNode')) {
            let isStartNode = urlParams.has('startNode');
            let nodeId = isStartNode ? urlParams.get('startNode') : urlParams.get('endNode');
            let moduleNode = nodeArray.find(function(m) { return m.id == nodeId });
            let downstreamModIds = self.getLinkedModules(nodeId, isStartNode, []);
            nodeArray = nodeArray.filter(m => downstreamModIds.includes(m.id));
        }
        let nodeArrayGroupIds = [...new Set(nodeArray.map(n => n.group))].filter(n => n);
        let groups = data.moduleNodeGroups.filter(ng => nodeArrayGroupIds.includes(ng.id));
        let finalArray = nodeArray.slice(0); //clone the array
        finalArray.push(...groups);
        return finalArray;
    }

    generateGroups(nodeArray) {
        var tmpGroupId = 1001;
        var groups = [];
        let applyGroupFunc = function(groupName, filterFunc) {
            let tmpArray = nodeArray.filter(filterFunc);
            if (tmpArray.length > 0) {
                let group = {id: tmpGroupId, text: groupName, isGroup: true};
                nodeArray.filter(filterFunc).forEach(n => n.group = group.id);
                groups.push(group);
                tmpGroupId++;
            }
        };
        applyGroupFunc("Usage Allocation (Sector)", n => n.name.startsWith("UsageAllocation") && n.level == "sector");
        applyGroupFunc("Usage Allocation (Site)", n => n.name.startsWith("UsageAllocation") && n.level == "site");
        applyGroupFunc("Clustering (Sector)",
            n => ["IdentifySectorNeighbour", "ClusterSectorNeighbour", "Reallocation", "ReallocationAnalysis"].includes(n.name) && n.level == "sector");
        applyGroupFunc("Clustering (Site)",
            n => ["ReallocationAnalysis"].includes(n.name) && n.level == "site");
        applyGroupFunc("Forecasting",
            n => ["VideoUsageRatio", "SectorForecast", "FillSectorForecast", "NationwideAdjustment", "SegmentForecast",
             "GrowthRateGen", "DemandForecastTxn"].includes(n.name));
        applyGroupFunc("Post-Process (Sector)",
            n => ["CombineUnitProperties", "CombineDecisionEngineOutputs"].includes(n.name) && n.level == "sector");
        applyGroupFunc("Target Network Utilization", n => n.name.indexOf("TargetNetworkUtilization") >= 0);
        applyGroupFunc("Pre-process FiveG",
            n => ["KpiFeatures", "DerivedGeoFeatures", "CellInventoryFeatures", "SplitUserTxnFourG",
             "XgboostRegression", "CombineFeatures", "SubscriberFeatures"].includes(n.name));
        applyGroupFunc("Config Gen (Site)", n => ["ConfigGenerator", "HybridApproachRollUp"].includes(n.name) && n.level == "site");
        applyGroupFunc("Revenue", n => ["UpgradeRevenueMultiYear", "QuarterlyRevenue"].includes(n.name));
        applyGroupFunc("Congestion", n => n.name.startsWith("Congestion_"));
        applyGroupFunc("Unit Database (Sector)", n => ["CellToUnit", "UnitProperties"].includes(n.name) && n.level == "sector");
        applyGroupFunc("Unit Database (Site)", n => ["CellToUnit", "UnitProperties"].includes(n.name) && n.level == "site");
        applyGroupFunc("CurrentUsage FiveG", n => ["CurrentUsageFiveGData", "CurrentUsageFiveGVoiceSms"].includes(n.name));
        return groups;
    }

    redraw(data, selectedStates) {
        let self = this;
        var nodeArray = self.getVisibleNodes(data, selectedStates);
        let fullname = $("#dag-node-labels").prop('checked');
        nodeArray.filter(n => !n.isGroup).forEach(n => n.text = (fullname ? n.name : n.initials));
        let nodeIds = nodeArray.map(n => n.id);
        let links = data.links.filter(l => nodeIds.includes(l.from) && nodeIds.includes(l.to));
        var mySavedModel = { "class": "go.GraphLinksModel",
          "nodeKeyProperty": "id",
          "nodeDataArray": nodeArray,
          "linkDataArray": links
        };
        myDiagram.startTransaction("change Model");
        myDiagram.clear();
        myDiagram.model = go.Model.fromJson(mySavedModel);
        myDiagram.updateAllRelationshipsFromData();
        myDiagram.updateAllTargetBindings();
        myDiagram.commitTransaction("change Model");
        myDiagram.commandHandler.zoomToFit();
        $("#moduleCount").text(nodeArray.length);

        if (selectedStates != null) {
            return;
        }
        let states = Object.keys(data.nodeStates);
        $("#legendContainer").html(states.map(s =>
        "<label for='checkbox-" + s + "' style='background-color: " + data.nodeStates[s] + "' class='legend'>" + s
            + "</label><input type='checkbox' name='legendCheckbox' id='checkbox-" + s + "' value='" + pipelineData.nodeStates[s] + "' checked>").join(""));
        $("#legendContainer input").checkboxradio();

         $("#modulesList").html(generateModulesList());

         $("#capex-version").html("CAPEX " + data.version);
         self.updateEventListeners();
         populateJsonTab("default");
         processQueryParameters();
    }

    updateEventListeners() {
        let self = this;
        $("#legendContainer input").change(function() {
            let selectedStates = $.map($('#legendContainer input:checked'), function(c){return c.value; });
            self.load(selectedStates);
        });
        let searchBoxData = Object.keys(pipelineData.runners).map(i => pipelineData.runners[i].appConf.name);
        searchBoxData.sort();
        $("#searchBox").autocomplete({
            source: searchBoxData,
            select: function (event, ui) { $(this).trigger("keyup"); },
            close: function (event, ui) { $(this).trigger("keyup"); }
        });
        let dataSearchBoxData = Object.keys(pipelineData.datasets);
        dataSearchBoxData.sort();
        $("#dataSearchBox").autocomplete({
            source: dataSearchBoxData,
            select: function (event, ui) { $(this).trigger("keyup"); },
            close: function (event, ui) { $(this).trigger("keyup"); }
        });
        $(".modSeqModule").click(function() {
            let value = $(this).attr('data');
            onNodeIdSelected(value);
        });

    }

    setOrientationEastward(eastward) {
        let direction = eastward ? 0 : 90;
        myDiagram.startTransaction("change Layout");
        myDiagram.layout.direction = direction;
        myDiagram.groupTemplate.layout.direction = direction;
        myDiagram.commitTransaction("change Layout");
    }

    showNodeFullNames(fullname) {
        let self = this;
        var nodeArray = self.getVisibleNodes(pipelineData);
        nodeArray.filter(n => !n.isGroup).forEach(n => n.text = (fullname ? n.name : n.initials));
        myDiagram.startTransaction("change Model");
        myDiagram.model.nodeDataArray = nodeArray;
        myDiagram.commitTransaction("change Model");
    }

    getLinkedModules(nodeId, isStartNode, processedIds) {
        let self = this;
        if (processedIds.filter(x => x == nodeId).length > 1) {
            return [nodeId];
        }
        var linkedNodeIds = isStartNode ? pipelineData.links.filter(link => link.from == nodeId).map(link => link.to) :
            pipelineData.links.filter(link => link.to == nodeId).map(link => link.from);
        var linkedNodeIdSet = new Set(linkedNodeIds);
        var finalIds = [];
        processedIds.push(...linkedNodeIdSet);
        linkedNodeIdSet.forEach(i => finalIds.push(...self.getLinkedModules(i, isStartNode, processedIds)));
        finalIds.push(Number(nodeId));
        return finalIds;
    }

}