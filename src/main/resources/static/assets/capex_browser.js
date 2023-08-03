var pipelineData = null;
var loadingDiv = "<div class='loading lds-ellipsis'><div></div><div></div><div></div><div></div></div>";
var myDiagram = null;
var dagViewer = new DagViewer();
var moduleDocs = null;

function getNodeTooltipText(s) {
    let node = pipelineData.runners[s];
    return node.appConf.name + "\n\nsequence no.: " + (node.sequenceNo >= 0 ? node.sequenceNo : "N/A") + "\nupstream modules:\n - " + node.upstream.join("\n - ") +
        "\ndownstream modules:\n - " + node.downstream.join("\n - ") + "\nstate: " + node.state + "\n";
}

function getLinkTooltipText(link) {
    return link.text + "\n\nFrom: " + link.fromName + "\nTo: " + link.toName;
}

function jsonToTable(jsonObj, parentClass = "") {
    var text = "<table class='table table-sm propertiesTable hover " + parentClass + "' border='1'>"
    if (jsonObj == null || jsonObj.length == 0) return "None";
    for (var x in jsonObj) {
        var rowText = (typeof jsonObj[x] === 'object'?
            jsonToTable(jsonObj[x], x) : escapeHtml(jsonObj[x]));
        text += "<tr><td class='key'>" + x + "</td><td class='value " + x + "-value'>" + rowText + "</td></tr>";
    }
    text += "</table>"
    return text;
}

function schemaToTable(jsonObj) {
    var text = "<table class='table table-sm small hover' border='1'>"
    text += "<thead class='ui-widget-header'><tr><th></th><th>Column</th><th>Type</th><th>Nullable</th></tr></thead>";
    var cnt = 0;
    var body = ""
    for (var x in jsonObj) {
        body += "<tr><td class='index'>" + (++cnt) + "</td><td class='key'>" + x + "</td><td>"
            + escapeHtml(jsonObj[x].type.replaceAll(",", ", ")) + "</td><td>" + jsonObj[x].nullable + "</td></tr>";
    }
    text += "<tbody class='ui-widget-content'>" + body + "</tbody></table>"
    return text;
}

function resetSidebar() {
    $("#detailsViewTitle").html("Click on a module or link to view its details.");
    $("#moduleDescription").html("");
    $(".btn-module-actions").hide();
    $("#accordionContainer").html("");
}

function onPathClicked(s) {
    $('#main a[href="#tabs-3"]').trigger('click');
    let kv = $(this).find('td.value').html();
    let path = kv.substring(kv.indexOf('=') + 1).trim();
    showDatasetDetails(path);
};
function onModuleClicked(s) {
    $('#main a[href="#tabs-1"]').trigger('click');
    var moduleName = $(this).find('td.value').html();
    if (moduleName == undefined) {
        moduleName = $(this).html();
    }
    $("#searchBox").val(moduleName);
    $("#searchBox").trigger('keyup');
};

function onNodeSelected(node) {
    if (!node.isSelected) {
        resetSidebar();
        return;
    }
    onNodeIdSelected(node.data.id);
}

function onNodeIdSelected(id) {
    var nodeDetails = pipelineData.runners[id];
    if (nodeDetails != null && nodeDetails.sequenceNo < 0)
        nodeDetails.sequenceNo = "N/A";
    let moduleName = nodeDetails.appConf.name;
    let moduleAbout = moduleDocs[moduleName];
    $("#detailsViewTitle").html(moduleName);
    $("#moduleDescription").html(generateModuleDescription(moduleAbout, moduleName));
    $("#seeMore_" + moduleName).click(function(){ $("#truncDesc_" + moduleName).hide(); $("#fullDesc_" + moduleName).show(); });
    $("#seeLess_" + moduleName).click(function(){ $("#truncDesc_" + moduleName).show(); $("#fullDesc_" + moduleName).hide(); });
    $(".btn-module-actions").show();
    $("#accordionContainer").remove("#accordion");
    let accordionDiv = $("<div id='accordion'></div>");
    accordionDiv.append("<h3>Details</h3><div class='detailsView'>" + jsonToTable(nodeDetails) + "</div>");
    $("#accordionContainer").html(accordionDiv);
    $("#accordion").accordion({collapsible: true});

    $(".inputPaths tbody tr").click(onPathClicked);
    $(".outputPaths tbody tr").click(onPathClicked);
    $(".upstream tbody tr").click(onModuleClicked);
    $(".downstream tbody tr").click(onModuleClicked);

    $(".state-value").css('background-color', pipelineData.nodeStates[$(".state-value").html()]);

    if (new URLSearchParams(window.location.search).get("node") != id) {
        window.history.pushState({}, null, "capex?node=" + id);
    }
}

function showDatasetDetails(path) {

    let ds = Object.values(pipelineData.datasets).find(function(s) { return s.path == path });
    $("#dataSearchBox").val(path);
    $("#dsAccordionContainer").remove("#dsTabs");

    let fromModule = Object.values(pipelineData.runners).find(r =>
         r.state != "Deprecated" && r.appConf.outputPaths.find(p => p.includes(path)) != undefined
     )
    let dependentModules = Object.values(pipelineData.runners).filter(r =>
        r.state != "Deprecated" && r.appConf.inputPaths.find(p => p.includes(path)) != undefined
    ).map(x => x.appConf.name)

    if (fromModule == null && dependentModules.length == 0) {
        $("#dsAccordionContainer").html(
            "<p id='dsTabs'>No details found for dataset <span class='bold'>'" + path + "'</bold></p>");
        return;
    }

    if (ds == null) {

        let rootDir = pipelineData.rootDirs.find(rd => path.startsWith(rd));
        $(".overlay").show();
        var startTime = Date.now();
        $.get( "/dataset/getDataFromPath",
            {"path" : path, "rootDir" : rootDir },
            function(content) {
                content.duration = (Date.now() - startTime) / 1000.0;
                $(".overlay").hide();
                ds = content;
                renderDatasetTab(path, ds, fromModule, dependentModules);
        }).fail(function (xhr, status, error) {
            $(".overlay").hide();
            alert(error + "\n" + xhr.responseText);
        });
        return;
    } else {
        renderDatasetTab(path, ds, fromModule, dependentModules);
    }
}

function onLinkSelected(link) {
    if (!link.isSelected) {
        resetSidebar();
        return;
    }
    let datasetNames = link.data.text.split("\n");
    let datasets = datasetNames.map(function(s) { return pipelineData.datasets[s.trim()] });
    let linkDetails = datasets.map(function(ds) { return ds != null ? schemaToTable(ds.schema) : "None" });
    $("#detailsViewTitle").html(link.data.text);
    $("#moduleDescription").html("");

    $("#accordionContainer").remove("#accordion");
    let accordionDiv = $("<div id='accordion'></div>");

    datasets.forEach(function (ds, i) {
        let dsName = datasetNames[i];
        let uniqueName = dsName.replaceAll(/\W/g, '_');
        let detailsView = "<div id='tab-" + uniqueName + "-1' class='detailsView'>"
            + jsonToTable({"format": (ds == null ? "N/A" : ds.format), "path": dsName,
               "from": pipelineData.runners[link.data.from].appConf.name,
               "to": pipelineData.runners[link.data.to].appConf.name }) + "<br/>Schema:<br/>"
            + linkDetails[i] + "</div>";
        let dataView = "<div id='tab-" + uniqueName + "-2' class='dataTable" + i + "'>"
            + "<table id='dataTable-" + uniqueName + "' class='hover compact'></table></div>";
        let dsTabs = "<div id='tabs-" + uniqueName + "'><ul>"
            + "<li><a href='#tab-" + uniqueName + "-1'>Details</a></li>"
            + "<li><a href='#tab-" + uniqueName + "-2'>Data</a></li></ul>"
            + detailsView + dataView + "</div>";
        $("<h3>" + dsName + "</h3>" + dsTabs).appendTo(accordionDiv);
    });
    $("#accordionContainer").html(accordionDiv);
    $("#accordion").accordion({
        collapsible: true,
        activate: function( event, ui ) {
            // Need to adjust DataTable column width to match the data when the table becomes visible
            $($.fn.dataTable.tables(true)).DataTable().columns.adjust();
        }
     });

    datasets.forEach(function (ds, i) {
        let dsName = datasetNames[i];
        let uniqueName = dsName.replaceAll(/\W/g, '_');
        renderDataTable(datasetNames[i], ds, ".dataTable" + i);
    });

    $(".from-value").click(onModuleClicked);
    $(".to-value").click(onModuleClicked);

    if (new URLSearchParams(window.location.search).get("link") != link.data.id) {
        window.history.pushState({}, null, "capex?link=" + link.data.id);
    }
}

function renderDatasetTab(path, ds, fromModule, dependentModules) {
    let uniqueName = ds.path.replaceAll(/\W/g, '_');
    let tableContainerId = 'tab-' + uniqueName + '-1';
    let dataView = "<div id='" + tableContainerId + "'>"
        + "<div class='input-group'>"
        + "<textarea class='form-control' placeholder='Enter your SQL statement here' id='sqlBox' rows='1'></textarea>"
        + "<button id='sqlBtn' class='btn btn-outline-secondary'>"
        + "<i class='fas fa-search'></i></button></div>"
        + "<div class='dataTable'><table></table></div></div>";
    let detailsView = "<div id='tab-" + uniqueName + "-2' class='detailsView'>"
        + jsonToTable({"format": (ds == null ? "N/A" : ds.format), "path": (ds == null ? path : ds.path),
           "from": fromModule != undefined ? fromModule.appConf.name : null,
           "to" : dependentModules })
        + "<br/>Schema:<br/>"
        + (ds != null ? schemaToTable(ds.schema) : "None" ) + "</div>";
    let filesView = "<div id='tab-" + uniqueName + "-3' class='filesView'><table></table></div>";
    let dsTabs = "<div id='dsTabs'><ul>"
        + "<li><a href='#" + tableContainerId + "'>Data</a></li>"
        + "<li><a href='#tab-" + uniqueName + "-2'>Details</a></li>"
        + "<li><a href='#tab-" + uniqueName + "-3'>Files</a></li></ul>"
        + dataView + detailsView + filesView + "</div>";

    $("#dsAccordionContainer").html(dsTabs);
    $("#dsTabs").tabs({
        activate: function( event, ui ) {
            // Need to adjust DataTable column width to match the data when the table becomes visible
            $($.fn.dataTable.tables(true)).DataTable().columns.adjust();
        }
     });

    renderDataTable(ds.path, ds, tableContainerId);
    renderFilesView(ds.path, 'tab-' + uniqueName + '-3');

    $(".from-value").click(onModuleClicked);
    $(".to .value").click(onModuleClicked);
    $("#sqlBox").val(ds.sql);
    $("#sqlBtn").click(function(){
        let sql = $("#sqlBox").val();
        $(".dataTables_wrapper").css("opacity", 0.3);
        loadingDiv.appendTo($('#' + tableContainerId));
        $('#' + tableContainerId + " .loading").show();
        var startTime = Date.now();
        $.post( "/dataset/queryTable", {"sql" : sql}, function(response) {
            response.query.duration = (Date.now() - startTime) / 1000.0;
            response.query.path = ds.path;
            $('#' + tableContainerId + " .dataTable").empty();
            $('#' + tableContainerId + " .dataTable").append("<table></table>");
            renderDataTable(ds.path, response.query, tableContainerId);
            $(".dataTables_wrapper").css("opacity", 1.0);
            $('#' + tableContainerId + " .loading").hide();
        }).fail(function (xhr, status, error) {
              $('#' + tableContainerId + " .loading").hide();
              alert(error + "\n" + xhr.responseText);
          });
    });

    $("#sqlBox").on('keyup', function (e) {
        if (e.key === 'Enter' && e.ctrlKey) $("#sqlBtn").trigger('click');
    });

    if (new URLSearchParams(window.location.search).get("data") != path) {
        window.history.pushState({}, null, "capex?data=" + path);
    }
}

function renderDataTable(dsName, ds, id) {
    let elemId = '#' + id;
    if (ds == null) {
        return;
    }
    let dataTableData = ds.data.map(function(d){ return Object.keys(ds.schema).map(function(k){
        return typeof d[k] === "object" ? JSON.stringify(d[k]) : d[k] }) });
    let dsColumns = Object.keys(ds.schema).map(function(k) {return { "title" : k, "defaultContent": "" }});
    if (dsColumns.length == 0) {
        return; // This is to avoid rendering a CSV DataTable with no columns, resulting to DataTable related JS errors
    }
    var isChartDisplayed = false;
    let table = $(elemId + " .dataTable table").DataTable( {
        dom: 'Blrtip',
        lengthMenu: [[15, 50], [15, 50]],
        pageLength: 15,
        columns:        dsColumns,
        data:           dataTableData,
        scrollY:        "90vh",
        scrollX:        true,
        scrollCollapse: true,
        scroller:       true,
        buttons: [
          {
              extend: 'colvis',
              titleAttr: 'Show/hide columns',
              columnText: function ( dt, idx, title ) {
                  return (idx+1)+': '+title;
              },
              text : '<i class="fa fa-list-check"></i>'

          },
          {
              text : '<i class="fa fa-refresh"></i>',
              titleAttr: 'Refresh table ' + dsName,
              action: function() {
                $(".overlay").show();
                let path = $("#dataSearchBox").val();
                let rootDir = pipelineData.rootDirs.find(rd => path.startsWith(rd));
                $.get( "/dataset/refreshTable",
                    {"table" : dsName, "rootPath": rootDir}, function() {
                    $(".overlay").hide();
                }).fail(function (xhr, status, error) {
                    $(".overlay").hide();
                    alert(error + "\n" + xhr.responseText);
                });
              }
          }, {
              extend: 'copy',
              titleAttr: 'Copy to clipboard',
              text : '<i class="fa fa-copy"></i>'
          }, {
              extend: 'csv',
              titleAttr: 'Export to CSV',
              text : '<i class="fa fa-file-csv"></i>',
              filename: dsName
          },
          {
              text: '<i class="fa fa-chart-line"></i>',
              titleAttr: 'View chart',
              action: function ( e, dt, node, config ) {
                $(elemId + ' .dataTables_wrapper').hide();
                $(elemId + ' .chart-container').show();
                if (!isChartDisplayed) {
                    initChartSettings(elemId, ds);
                    isChartDisplayed = true;
                }
              }
           }
        ],
        infoCallback: function( settings, start, end, max, total, pre ) {
           let duration = ds.duration ? ds.duration : 0;
           var info = "Query took " + duration + " sec(s). Showing " + start + " to " + end + " of " + total + " entries";
           return info
        },
        // Use this to properly align data with columns
        drawCallback: function( settings ) {
          // Reset margin to 0 after datatable render
          var ele = document.querySelector('.dataTables_scrollBody');
          if(ele){
             ele = ele.querySelector('.dataTable');
             if(ele){
                ele.style.margin = 0;
             }
         }
       }
   } ).on( 'draw', function () {
       $(elemId + ' tbody tr').click(function(event) {
           $(this).addClass('row-selected').siblings().removeClass('row-selected');
       });
       $(elemId + ' tbody tr td').dblclick(function(){
           let rowData = extractRowData(table, $(this), Object.keys(ds.schema));
           showRowDetailsDialog(".modal-row-details", dsName, rowData);
       });
    } );

    $(elemId + " .dt-buttons button").tooltip();

    $(elemId + ' tbody tr').click(function(event) {
        $(this).addClass('row-selected').siblings().removeClass('row-selected');
    });
    $(elemId + ' tbody tr td').dblclick(function(){
       let rowData = extractRowData(table, $(this), Object.keys(ds.schema));
       showRowDetailsDialog(".modal-row-details", dsName, rowData);
    });

    $(elemId + " .chart-container").remove();
    let overlayDiv = $("#chartDivTemplate .chart-container").clone();
    overlayDiv.appendTo($(elemId));
    overlayDiv.hide();
    let chartSettingsId = elemId.substring(1) + "-chartsettings";
    $('#' + chartSettingsId).remove();
    $(elemId + " .modal-chart-settings").attr('id', chartSettingsId);
    $(elemId + ' .btn-view-table').click(function(){
        $(elemId + ' .dataTables_wrapper').show();
        $(elemId + ' .chart-container').hide();
    });
    $(elemId + ' .btn-chart-settings').click(function(){
        $('#' + chartSettingsId).dialog({
            title: "Chart Settings - " + dsName,
            width: "600px",
            open: function(event, ui) {
                $(this).parent().css({'top': window.pageYOffset+60});
            }
         });
    });
   $(elemId + ' .btn-fullscreen').on('click', function(){
     if (document.fullscreenElement) {
       document.exitFullscreen();
     } else {
       $(elemId + ' .chart-container').get(0).requestFullscreen();
     }
   });
}

function renderFilesView(rootDir, divId) {
   var dsPath = $("#dataSearchBox").val();
   if (dsPath.endsWith(".csv") && dsPath.indexOf("/") < 0) {
      dsPath = $("#input-path-edit").val() + "/conf/" + dsPath;
   }
   let tableAppProps = $("#" + divId + " table").DataTable( {
      dom: 'Bfrtip',
      pageLength: 30,
      columns: [{title: "filename"}, {title: "date"}, {title: "size"},
      {
          data: null,
          render: function(data, type, row, meta){
            if(type === 'display'){
                data = '<a href="dataset/download?path=' + data[3] + '" download="' + data[0] + '"><i class="fa fa-download"></i></a>';
            }
            return data;
          },
          orderable: false
      }],
      ajax: '/dataset/listFiles?path=' + dsPath,
      buttons: [
         {
             text: "<i class='fa fa-refresh'></i> <span>Refresh</span>",
             action: function (e, dt, node, config) {
                 dt.ajax.reload(null, false);
             }
         }
      ],
   } ).on( 'draw', function () {
     $('#table-app-props tbody tr').click(function(event) {
         $(this).addClass('row-selected').siblings().removeClass('row-selected');
     });
  } );
}

window.onpopstate = function() {
        $( "#main" ).tabs({ active: 0 });
        processQueryParameters();
    };

$(function() {
    $.fn.bootstrapBtn = $.fn.button.noConflict();
    loadingDiv = $(loadingDiv);
    $.get( "/data/module_docs.json",
       function(content) {
         moduleDocs = content.reduce(function(map, obj) {
             map[obj.name] = obj;
             return map;
         }, {});
    }).fail(function (xhr, status, error) {
       console.log(error + "\n" + xhr.responseText);
    });
    dagViewer.initialize();
    $('#searchBox').keyup(function() {
        textToSearch = $(this).val().toLowerCase();
        if (textToSearch.length < 4) return;
        var node = myDiagram.nodes.filter(function(n) {
            return !n.data.isGroup && n.data.name.toLowerCase().indexOf(textToSearch) >= 0
        }).first();
        if (node != null) {
            myDiagram.select(node);
            myDiagram.commandHandler.resetZoom();
            myDiagram.commandHandler.scrollToPart(node);
        }

    });
    $('#dataSearchBtn').click(function () {
        showDatasetDetails($('#dataSearchBox').val());
    });
    $('#dataSearchBox').keyup(function (e) {
        if (e.key === 'Enter') $("#dataSearchBtn").trigger('click');
    });

    $("#mySidebar").resizable({ handles: "e" });
    $("#main").tabs();

    $("#profile").change(function() {
        let selectedStates = $.map($('#legendContainer input:checked'), function(c){return c.value; });
        dagViewer.load(selectedStates);
    });

    $("#showLegendDetails").click(function(){
        $("#legendModal").dialog({
          title: "Legend", modal: true,
          height: 500,
          width: 700,
          buttons: {
            OK: function() {
              $( this ).dialog( "close" );
            }
          }});
    });

    $("#update-path-btn").click(function(){
        $("#input-path").val($("#input-path-edit").val());
        pipelineData = null;
        dagViewer.load();
    });

    $("#input-path-edit").on('keyup', function (e) {
        if (e.key === 'Enter') $("#update-path-btn").trigger('click');
    });

    $.get( "/capex/capexDirHistory",
        function(content) {
          $("#input-path-edit").autocomplete({
            source: content,
            select: function (event, ui) { $(this).trigger("keyup"); },
            close: function (event, ui) { $(this).trigger("keyup"); }
          });
    }).fail(function (xhr, status, error) {
        alert(error + "\n" + xhr.responseText);
    });

    $('.btn-module-actions button').tooltip();
    $('.btn-view-upstream-modules').click(function(){
        let nodeId = $('.sequenceNo-value').text();
        window.history.pushState({}, null, "capex?node=" + nodeId + "&endNode=" + nodeId);
        dagViewer.load();
    });
    $('.btn-view-downstream-modules').click(function(){
        let nodeId = $('.sequenceNo-value').text();
        window.history.pushState({}, null, "capex?node=" + nodeId + "&startNode=" + nodeId);
        dagViewer.load();
    });
    $('.btn-reset-view').click(function(){
        let nodeId = $('.sequenceNo-value').text();
        window.history.pushState({}, null, "capex?node=" + nodeId);
        dagViewer.load();
    });

    $('input[name="radio-capex-json"]').change(function(){
        populateJsonTab(this.value);
   });
   $('#dag-orientation').change(function(){
        dagViewer.setOrientationEastward($(this).prop('checked'));
   });
   $('#dag-node-labels').change(function(){
        dagViewer.showNodeFullNames($(this).prop('checked'));
   });

    $('.btn-dag-fullscreen').tooltip();
    $('.btn-dag-fullscreen').click(function(){
       if (document.fullscreenElement) {
         document.exitFullscreen();
       } else {
         $('#myDiagramDiv').get(0).requestFullscreen();
       }
    });
    $('.btn-dag-export-image').click(function(){
        var blob = myDiagram.makeImageData({ scale: 1, background: "white", returnType: "blob",
          maxSize: new go.Size(Infinity, Infinity), callback: function(blob1){
             var url = window.URL.createObjectURL(blob1);
             var filename = "CAPEX_" + pipelineData.version + ".png";

             var a = document.createElement("a");
             a.style = "display: none";
             a.href = url;
             a.download = filename;

             document.body.appendChild(a);
             requestAnimationFrame(() => {
               a.click();
               window.URL.revokeObjectURL(url);
               document.body.removeChild(a);
             });
          }
        });
    });
});

var entityMap = {
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#39;',
  '/': '&#x2F;',
  '`': '&#x60;',
  '=': '&#x3D;'
};

function escapeHtml (string) {
  return String(string).replace(/[&<>"'`=\/]/g, function (s) {
    return entityMap[s];
  });
}

function processQueryParameters() {
    var urlParams = new URLSearchParams(window.location.search);
    if(urlParams.has('node')) {
        let nodeId = urlParams.get('node');
        let moduleNode = pipelineData.moduleNodes.find(function(m) { return m.id == nodeId });
        $("#searchBox").val(moduleNode.name);
        $("#searchBox").trigger('keyup');
    }
    else if(urlParams.has('link')) {
        let linkId = urlParams.get('link');
        let link = { "data": pipelineData.links.find(function(m) { return m.id == linkId }), "isSelected" : true };
        onLinkSelected(link);
    }
    else if (urlParams.has('data')) {
        $('#main a[href="#tabs-3"]').trigger('click');
        let path = urlParams.get('data');
        showDatasetDetails(path);
    }
}

function generateModulesList() {
    var modulesTable = "<table><thead>" + Object.keys(pipelineData.profiles).map(function(p) { return "<th>" + p + "</th>" }).join("") + "</thead>";
    let profileRunners = Object.keys(pipelineData.profiles).map(function(p) {
        var runners = pipelineData.profiles[p].map(function(rIdx){ return pipelineData.runners[rIdx]})
            .map(function(r) {
                return "<div class='modSeqModule ui-button' data='" + r.sequenceNo + "'><span class='modName'>" + r.sequenceNo + ". " + r.appConf.name + "</span><br/>"
                    + "<span>" + r.runner.shellScript + "</span></div><br/>";
             }).join("");
        return "<td>" + runners + "</td>";
    }).join("");
    modulesTable += "<tbody><tr>" + profileRunners + "</tr></tbody></table>";
    return modulesTable;
}

function populateJsonTab(format) {

    let validRunners = Object.values(pipelineData.runners)
        .filter(r => ['Normal', 'Start', 'End'].includes(r.state) && r.appConf.name != "Start");
    let runnerNames = validRunners.map(r => r.appConf.name);
    let validLinks = pipelineData.links.filter(l => runnerNames.includes(l.fromName) && runnerNames.includes(l.toName));
    var finalJson = {};
    if (format == "default") {
        finalJson = {
            "version": pipelineData.version, "runners": validRunners,
            "links": validLinks.map(l => {return {"from": l.fromName, "to": l.toName}; })
        };
    }
    else if (format == "airflow") {
        let functionalGroups = Object.assign({}, ...validRunners.map(r => {
            return {[r.appConf.name] : {
                "task" : {[r.appConf.name] : {
                    "operator": (r.runner.type == "SparkJob" ? "livybatchoperator" : "batch_with_conf"),
                    "class_name": r.runner.entryPointClass,
                    "conf": r.runner.configFiles,
                    "csv": r.appConf.inputPaths.filter(f => f.endsWith(".csv")).map(f => f.split("=")[1].trim()),
                    "control": {
                        "variable": "Profile", "run_on": r.profiles
                    }
                },
                "flow": []}
            }};
        }));
        finalJson = {
            "functional_groups": functionalGroups,
            "flow": validLinks.map(l => [l.fromName, l.toName])
        };
    }
    $(".capex-json").val(JSON.stringify(finalJson, null, 4));
}

function generateModuleDescription(moduleAbout, moduleName) {
    if (moduleAbout == undefined) return "";
    let htmlDesc = moduleAbout.description.replaceAll("\n", "<br/>");
    let htmlLink = "More details here: <a href='" + moduleAbout.link + "' target='_blank'>" + moduleName + "</a>";
    let truncatedHtmlDesc = htmlDesc.length < 100 ? htmlDesc : moduleAbout.description.slice(0, 100);
    let fullDesc = htmlDesc + "<br/><br/>" + htmlLink;
    return "<span id='truncDesc_" + moduleName + "'>" + truncatedHtmlDesc + "... <a class='btn btn-sm btn-link ui-helper-reset' id='seeMore_" + moduleName + "'>See more</a></span>"
        + "<span id='fullDesc_" + moduleName + "' style='display: none'>" + fullDesc + "<br/><a class='btn btn-sm btn-link ui-helper-reset' id='seeLess_" + moduleName + "'>See less</a></span>";
}