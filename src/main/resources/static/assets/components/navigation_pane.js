class NavigationPane {

    initialize() {
        let self = this;
        let btnActions = $("#file-list-actions");
        $("#file-list-actions .btn").tooltip()
        btnActions.find(".btn-read").click(function(e){
            let rootDir = btnActions.attr("rootDir");
            if (btnActions.attr("act-on") == "file-item") {
                let node = { text: btnActions.attr("filename"), path: rootDir };
                getDataFromDataset(node, rootDir);
            }
        });
        btnActions.find(".btn-unmount").click(function(e){
            let rootDir = btnActions.attr("rootDir");
            if (confirm("Are you sure you want to unmount this path from your workspace?\n" + rootDir)) {
                $(".overlay").show();
                $.post( "/dataset/unmount", {path: rootDir}, function( data ) {
                    $(".overlay").hide();
                    treeData = data;
                    self.updateNavPaneDatasets(data);

                }).fail(function (xhr, status, error) {
                    $(".overlay").hide();
                    alert(error + "\n" + xhr.responseText);
                });
            }
        });
        btnActions.find(".btn-refresh").click(function(e){
            let rootDir = btnActions.attr("rootDir");
            $(".overlay").show();
            if (btnActions.attr("act-on") == "file-item") {
                let node = { text: btnActions.attr("filename"), path: rootDir };
                $.get( "/dataset/refreshTable",
                    {"table" : node.text, "rootPath": rootDir}, function() {
                    $(".overlay").hide();
                }).fail(function (xhr, status, error) {
                    $(".overlay").hide();
                    alert(error + "\n" + xhr.responseText);
                });
            } else {
                $.get( "/dataset/list", {path: rootDir, refresh: true}, function( data ) {
                    $(".overlay").hide();
                    treeData = data;
                    self.updateNavPaneDatasets(treeData);
                }).fail(function (xhr, status, error) {
                    $(".overlay").hide();
                    alert(error + "\n" + xhr.responseText);
                });
            }
        });

        btnActions.find(".btn-schema").click(function(e){
            $('.popover').hide();
            let rootDir = btnActions.attr("rootDir");
            $(".overlay").show();
            if (btnActions.attr("act-on") == "file-item") {
                let node = { text: btnActions.attr("filename"), path: rootDir };
                $.get( "/dataset/schema",
                    {"table" : node.text, "rootPath": rootDir}, function(schema) {
                    $(".overlay").hide();
                    $('.modal-schema .schema-table').text(node.text);
                    $('.modal-schema .schema').html(schemaToTable(schema));
                    $('.modal-schema').dialog({
                        title: "Schema - " + node.text,
                        width: 400,
                        height: 400
                     });
                }).fail(function (xhr, status, error) {
                    $(".overlay").hide();
                    alert(error + "\n" + xhr.responseText);
                });
             }
        });
        btnActions.find(".btn-tmplt-code").click(function(e){
            let rootDir = btnActions.attr("rootDir");
            if (btnActions.attr("act-on") == "file-item") {
                let node = { text: btnActions.attr("filename"), path: rootDir };
                let currSection = wb.tabs[wb.currentTab].currentSection;
                let sql = $(this).text().replace("{table}", node.text);
                $(currSection + " .search-box").val(sql);
                $(currSection + " .search-btn").trigger("click");
            }
        });
        btnActions.find(".btn-tmplt-select-cols").click(function(e){
            let rootDir = btnActions.attr("rootDir");
            if (btnActions.attr("act-on") == "file-item") {
                let node = { text: btnActions.attr("filename"), path: rootDir };
                self.generateAndRunSql(node, rootDir, "SELECT_COLS");
            }
        });
        btnActions.find(".btn-tmplt-cache-table").click(function(e){
            let rootDir = btnActions.attr("rootDir");
            if (btnActions.attr("act-on") == "file-item") {
                let node = { text: btnActions.attr("filename"), path: rootDir };
                self.generateAndRunSql(node, rootDir, "CACHE_TABLE");
            }
        });
        btnActions.find(".btn-tmplt-pivot").click(function(e){
            let language = $(this).attr("language");
            $('.popover').hide();
            let rootDir = btnActions.attr("rootDir");
            if (btnActions.attr("act-on") == "file-item") {
                let node = { text: btnActions.attr("filename"), path: rootDir };
                $.get( "/dataset/schema",
                    {"table" : node.text, "rootPath": rootDir}, function(schema) {
                    $(".overlay").hide();
                    let dimListUi = $('.modal-sql-pivot .dimension-list');
                    dimListUi.empty();
                    Object.keys(schema).sort().forEach(function(k){
                        dimListUi.append($('<li class="btn btn-sm btn-secondary" draggable="true" ondragstart="dragSqlColumn(event)">' + k + '</li>'));
                    });
                    let aggFuncListUi = $('.modal-sql-pivot .aggfunc-list');
                    aggFuncListUi.empty();
                    let aggFuncList = ["avg()", "max()", "min()", "sum()", "count()", "approx_count_distinct()",
                        "first()", "last()", "greatest()", "least()", "kurtosis()", "mean()", "percentile(,0.5)",
                         "percentile_approx(, 0.5)",
                        "skewness()", "std()", "stddev_pop()", "variance()", "var_pop()"];
                    aggFuncList.sort().forEach(function(f){
                        aggFuncListUi.append($('<li class="btn btn-sm btn-secondary" draggable="true" ondragstart="dragSqlAggFunc(event)">' + f + '</li>'));
                    });
                    $('.modal-sql-pivot').dialog({
                        title: "SQL Editor - " + node.text,
                        width: 600,
                        height: 600,
                        buttons: {
                            Execute: function() {
                                let cols = $('.modal-sql-pivot .field-cols').val();
                                let rows = $('.modal-sql-pivot .field-rows').val();
                                let aggs = $('.modal-sql-pivot .field-aggs').val();
                                if (!cols) {
                                    alert("At least one entry in the Columns field should be supplied.");
                                } else if (!aggs) {
                                    alert("At least one entry in the Aggregates field should be supplied.");
                                } else {
                                    let currSection = wb.tabs[wb.currentTab].currentSection;
                                    $(currSection + " .loading").show();
                                    self.generateAndRunSql(node, rootDir, "PIVOT",
                                        {"cols": cols, "rows": rows, "aggs": aggs, "language": language});
                                }
                            },
                            Close: function() {
                              $( this ).dialog( "close" );
                            }
                        }
                     });
                }).fail(function (xhr, status, error) {
                    $(".overlay").hide();
                    alert(error + "\n" + xhr.responseText);
                });
            }
        });
        btnActions.find(".btn-tmplt-df-show").click(function(e){
            let rootDir = btnActions.attr("rootDir");
            if (btnActions.attr("act-on") == "file-item") {
                let node = { text: btnActions.attr("filename"), path: rootDir };
                let currSection = wb.tabs[wb.currentTab].currentSection;
                let code = "%scala\n" +
                    "val df = spark.read.table(\"" + node.text + "\")\n" +
                    "df.limit(100)";
                $(currSection + " .search-box").val(code);
                $(currSection + " .search-btn").trigger("click");
            }
        });
        btnActions.tooltip();

        $("#btn-file-filter").click(function(e) {
            e.preventDefault();
            self.onSearchDataset(self);
        });
        if (!jsEventsMinimize) {
            $("#file-list-filter").keyup(function(e) {
              self.onSearchDataset(self);
            });
        }

        $(document).on('click', function (e) {
            $('[data-bs-toggle="popover"]').each(function () {
                //the 'is' for buttons that trigger popups
                //the 'has' for icons within a button that triggers a popup
                if (!$(this).is(e.target) && $(this).has(e.target).length === 0 && $('.popover').has(e.target).length === 0) {
                    $("#file-list-actions-hideout").append(btnActions);
                    $(this).popover("hide");
                    $('.popover').hide();
                }
            });
        });
    }

    updateNavPaneDatasets(tmpData, expand) {
        $("#file-list-actions-hideout").append($("#file-list-actions"));
        let rnFileGroupNav = tmpData.map((rn, i) => {
            let collapseId = 'collapse' + i;
            let rnBadge = ' <span class="badge">' + rn.tags[0] + '</span>';
            let rnProps = '<span class=\'text-muted\'>Dataset count: </span><span>' + rn.nodes.length + '</span><br/>' +
                '<span class=\'text-muted\'>Total size: </span><span>' + rn.size + '</span><br/>';
            let rnBtnToggle = '<span class="btn-toggle rounded" style="padding-bottom: 10px" data-bs-toggle="collapse" data-bs-target="#'
             + collapseId + '" ><span class="file-group-name" data-bs-toggle="popover" title="' + rn.text + '" data-bs-content="<div class=\'btn-actions\'></div>' + rnProps + '">' + truncate(rn.text, 45) + '</span>' + rnBadge + '</span>';
            let liList = rn.nodes.map(cn => {
                let props = '<span class=\'text-muted\'>Path: </span><span>' + cn.path + '</span><br/>' +
                    '<span class=\'text-muted\'>Format: </span><span>' + cn.format + '</span><br/>' +
                    '<span class=\'text-muted\'>Size: </span><span>' + cn.size + '</span><br/>';
                return '<li class="file-item rounded"><i class="fa fa-table"></i> <span class="filename-label" data-bs-toggle="popover" title="' + cn.text + '" data-bs-content="<div class=\'btn-actions\'></div>' + props + '" path="' + cn.path + '">' + cn.text + '</span></li>';
            }).join('');
            let show = expand ? " show" : "";
            let hideRootNode = rn.tags[0] == 0 ? " d-none" : "";
            let collapseDiv = '<ul class="btn-toggle-nav list-unstyled collapse ' + show + '" id="' + collapseId + '">'
                + liList + '</ul>';
            return '<li class="file-group ' + hideRootNode + '">' + rnBtnToggle + collapseDiv + '</li>';
        }).join('');
        $('.file-list-sidebar').empty();
        $('.file-list-sidebar').append(rnFileGroupNav);

        let btnActions = $("#file-list-actions");
         $('.file-list-sidebar .btn-toggle-nav li').click(function(){
            btnActions.attr("act-on", "file-item");
            btnActions.attr("rootDir", $(this).closest('.file-group').find(".file-group-name").attr('data-bs-original-title').trim());
            btnActions.attr("filename", $(this).find('.filename-label').attr('data-bs-original-title'));
         });
         $('.file-list-sidebar .btn-toggle').click(function(){
            btnActions.attr("act-on", "file-group");
            btnActions.attr("rootDir", $(this).find(".file-group-name").attr('data-bs-original-title').trim());
         });
         $('.file-list-sidebar [data-bs-toggle="popover"]').popover({
          placement: "bottom",
          fallbackPlacements: ["bottom", "top"],
          html: true,
          customClass: 'nav-popover',
          template: '<div class="popover" role="tooltip"><div class="popover-arrow"></div>'
            + '<h4 class="popover-header"></h4>'
            + '<div class="popover-body"></div></div>',
          animation: false}).on('shown.bs.popover', function (e) {
                 $('[data-bs-toggle="popover"]').not(this).popover('hide');
                 if (btnActions.attr("act-on") == "file-item") {
                    $('.btn-read').show(); $('.btn-schema').show(); $('.btn-unmount').hide();
                    $('.btn-sql-templates').show();
                    $('.btn-refresh').attr('data-bs-original-title', "Refresh dataset");
                 } else {
                    $('.btn-read').hide(); $('.btn-schema').hide(); $('.btn-unmount').show();
                    $('.btn-sql-templates').hide();
                    $('.btn-refresh').attr('data-bs-original-title', "Refresh collection");
                 }
                 let popoverId = $(this).attr("aria-describedby");
                 $('#' + popoverId).find('.btn-actions').append(btnActions);
             });
    }

    updateNavPaneTabs(tmpTabs) {
        $('.tab-list-sidebar').empty();
        let tabListNav = tmpTabs.map(function(t){
            let sectionListNav = t.sectionOrder.map(function(so){
                let s = t.sections[so];
                let sectionUi = '<li class="file-item rounded">'
                    + '<span class="filename-label" ref="' + s.sectionId + '">'
                    + '<i class="fa fa-indent"></i><span> ' + s.sectionName + '</span>'
                    + '</span>'
                    + '</li>';
                return sectionUi;
            }).join('');

            let collapseId = 'collapse_' + t.tabId.substring(1);
            let tabName = '<span class="file-group-name">' + t.tabName + '</span>';
            let tBadge = '<span class="badge">' + t.sectionOrder.length + '</span>';
            let tab = '<li class="file-group">'
                + '<span class="btn-toggle rounded" ref="' + t.tabId + '" data-bs-toggle="collapse" data-bs-target="#' + collapseId + '">' + tabName + tBadge + '</span>'
                    + '<ul class="btn-toggle-nav list-unstyled collapse" id="' + collapseId + '">'
                    + sectionListNav
                    + "</ul>"
                +'</li>';
            return tab;
        }).join('');
        $('.tab-list-sidebar').append(tabListNav);
        $('.tab-list-sidebar .filename-label').click(function(){
            let tabId = $(this).closest('.file-group').find('.btn-toggle').attr('ref');
            let sectionId = $(this).attr('ref');
            $(tabId).tab('show');
            $(sectionId)[0].scrollIntoView();
        });
    }

    filterTreeData(data, textToSearch) {
        let txtSearch = textToSearch.toLowerCase();
        return data.map(function(rNode){
            let cNodes = rNode.nodes.filter(cn => cn.text.toLowerCase().includes(txtSearch));
            let rNodeClone = $.extend(true, {}, rNode);
            rNodeClone.nodes = cNodes;
            rNodeClone.tags = [cNodes.length];
            return rNodeClone;
        });
    }

    searchTabsForText(tabs, textToSearch) {
        let txtSearch = textToSearch.toLowerCase();
        let ft = tabs.map(function(t){
            let nt = Object.assign({}, t);
            let fSectionList = Object.values(t.sections).filter(function(s){
                return [s.sectionName, s.description, s.sql].join(" ").toLowerCase().includes(txtSearch);
            });
            nt.sections = fSectionList.reduce(function(map, s) {
                map[s.sectionId] = s;
                return map;
            }, {});
            let fSectionIds = Object.keys(nt.sections);
            nt.sectionOrder = t.sectionOrder.filter(s => fSectionIds.includes(s));
            console.log(nt);
            return nt;
        }).filter(t => t.sectionOrder.length > 0);
        return ft;
    }

    onSearchDataset(parent) {
        let textToSearch = $("#file-list-filter").val().toLowerCase();
        let filteredTreeData = parent.filterTreeData(treeData, textToSearch);
        parent.updateNavPaneDatasets(filteredTreeData, true);
        let filteredTabs = parent.searchTabsForText(wb.tabOrder.map(t => wb.tabs[t]), textToSearch);
        parent.updateNavPaneTabs(filteredTabs);
    }

    generateAndRunSql(node, rootDir, templateType, extraParams) {

        let params = {"table" : node.text, "rootPath": rootDir, "type": templateType};
        let finalParams = Object.assign(params, extraParams)
        $.get( "/dataset/generateSql", finalParams, function(resp) {
            let currSection = wb.tabs[wb.currentTab].currentSection;
            $(currSection + " .search-box").val(resp.sql);
            $(currSection + " .search-btn").trigger("click");
        }).fail(function (xhr, status, error) {
            $(".overlay").hide();
            alert(error + "\n" + xhr.responseText);
        });
    }
}
