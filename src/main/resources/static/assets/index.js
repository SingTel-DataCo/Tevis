var datasets = null;
var treeData = null;
var sqlHistory = [];
var loadingDiv = "<div class='loading lds-ellipsis'><div></div><div></div><div></div><div></div></div>";
var navPane = new NavigationPane();

//workbook
var wb = { tabOrder: [],
  currentTab: null,
  tabs: {},
  tabCounter: 0,
  sectionCounter: 0
};

$(function() {
    $.fn.bootstrapBtn = $.fn.button.noConflict();
    loadingDiv = $(loadingDiv);
    $("#btn-show-modal-new-ds").click(showNewDatasetModal);
    $("#mySidebar").resizable({ handles: "e" });
    navPane.initialize();
    $('.dataTable table').DataTable();
    $("#btn-add-dataset").click(function(e) {
        e.preventDefault();
        e.stopPropagation();
        if ($('#form-new-dataset')[0].reportValidity()) {
            getAllDatasets($("#new-dataset-path").val());
            $('.modal-new-dataset').dialog("close");
        } else {
        }
     });
    $("#new-dataset-path").on('keyup', function (e) {
        if (e.key === 'Enter') $("#btn-add-dataset").trigger('click');
    });
    syncWork(function(wb){
       if (wb.tabOrder.length == 0) {
           wb.tabCounter++;
           createNewTab(wb.tabCounter, true);
       } else {
           // Don't pass wb as parameter because it passes by value and not by reference
           createTabs();
       }
    });

    getAllDatasets($("#input-path").val());

    $("#btn-add-tab").click(function(){
        wb.tabCounter++;
        createNewTab(wb.tabCounter);
    });

    $( ".nav-tabs" ).sortable({
        containment: ".nav-tabs",
        tolerance: "pointer",
        cursor: "move",
        opacity: 0.5,
        update: function(event, ui) {
            var arrayOfIds = $.map($("#main .ui-tab .nav-link"), function(n, i){
              return '#' + n.id;
            });
            wb.tabOrder = arrayOfIds;
            syncWork();
          // Get the new order of the accordion items and save it to the server/database
        }
    });
    $(".tab-actions button").tooltip();

    $('.btn-new-section').click(function(){
        wb.sectionCounter++;
        createNewSection(wb.currentTab, wb.sectionCounter);
    });

    $(".btn-toggle-collapse-all").click(function(){
        let tabContentId = wb.tabs[wb.currentTab].tabContentId;
        let numPanelOpen = $(tabContentId + ' .accordion-item .show').length;
        console.log(numPanelOpen);
        let command = numPanelOpen > 0 ? "hide" : "toggle";
        wb.tabs[wb.currentTab].sectionOrder.map(sId => {
            $(sId + "-collapse").collapse(command);
        });
    });

    $('.btn-share-tab').click(function(){
        let tab = wb.tabs[wb.currentTab];
        shareLink('tab - ' + tab.tabName, wb.currentTab, null, this);
    });

    $('.btn-copy-link').click(function(){
      var copyText = document.getElementById("link-box");
      copyText.select();
      copyText.setSelectionRange(0, 99999)
      document.execCommand("copy");
    });

//    var map = L.map('map').setView([37.857142857142854,20.0], 11);
//    var CartoDB_Positron = L.tileLayer('https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png', {
//    	attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/attributions">CARTO</a>',
//    	subdomains: 'abcd',
//    	maxZoom: 20
//    }).addTo(map);
//    let gjsonData2 = {"type":"MultiPolygon","coordinates":[[[[37.857142857142854,20.0],[35.0,10.0],[10.0,20.0],[37.857142857142854,20.0]]],[[[15.0,20.0],[10.0,20.0],[15.0,40.0],[15.0,20.0]]],[[[20.0,20.0],[15.0,20.0],[15.0,30.0],[20.0,30.0],[20.0,20.0]]],[[[26.428571428571427,20.0],[20.0,20.0],[20.0,30.0],[26.428571428571427,23.571428571428573],[26.428571428571427,20.0]]],[[[15.0,30.0],[15.0,40.0],[20.0,40.0],[20.0,30.0],[15.0,30.0]]],[[[20.0,40.0],[26.428571428571427,40.0],[26.428571428571427,32.142857142857146],[20.0,30.0],[20.0,40.0]]],[[[37.857142857142854,20.0],[30.0,20.0],[34.04761904761905,32.142857142857146],[37.857142857142854,32.142857142857146],[37.857142857142854,20.0]]],[[[34.04761904761905,34.682539682539684],[26.428571428571427,32.142857142857146],[26.428571428571427,40.0],[34.04761904761905,40.0],[34.04761904761905,34.682539682539684]]],[[[34.04761904761905,32.142857142857146],[35.0,35.0],[37.857142857142854,35.0],[37.857142857142854,32.142857142857146],[34.04761904761905,32.142857142857146]]],[[[35.0,35.0],[34.04761904761905,34.682539682539684],[34.04761904761905,35.0],[35.0,35.0]]],[[[34.04761904761905,35.0],[34.04761904761905,40.0],[37.857142857142854,40.0],[37.857142857142854,35.0],[34.04761904761905,35.0]]],[[[30.0,20.0],[26.428571428571427,20.0],[26.428571428571427,23.571428571428573],[30.0,20.0]]],[[[15.0,40.0],[37.857142857142854,43.80952380952381],[37.857142857142854,40.0],[15.0,40.0]]],[[[45.0,45.0],[37.857142857142854,20.0],[37.857142857142854,43.80952380952381],[45.0,45.0]]]]};
//    var geojsonLayer = L.geoJson(gjsonData2, {
//        coordsToLatLng: function (coords) {
//            return new L.LatLng(coords[0], coords[1], coords[2]);
//        }
//    }).bindPopup(function (layer) {
//        return "Heyy"; //layer.feature.properties.description;
//    }).addTo(map);
});

function renderDataTable(rootId, dsName, jsonContent, chartModel) {
    let columns = Object.keys(jsonContent.schema);
    let dataTableData = jsonContent.data.map(function(d){ return columns.map(function(k){
        return typeof d[k] === "object" ? JSON.stringify(d[k]) : d[k] }) });
    let dsColumns = columns.map(function(k) {return { "title" : k, "defaultContent": "" }});
    let elemId = rootId + " .dataTable";
    let domTable = $(elemId);
    domTable.empty();
    $("<table></table>").appendTo(domTable);
    let table = $(elemId + " table").DataTable( {
       dom: 'Blrtip',
       lengthMenu: [[10, 50], [10, 50]],
       pageLength: 10,
       columns:        dsColumns,
       data:           dataTableData,
       scrollY:        "50vh",
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
              extend: 'copy',
              titleAttr: 'Copy to clipboard',
              text : '<i class="fa fa-copy"></i>'
          }, {
              extend: 'csv',
              titleAttr: 'Export to CSV',
              text : '<i class="fa fa-file-csv"></i>',
              filename: $(rootId + " .acc-title").text()
          },
          {
              text: '<i class="fa fa-chart-line"></i>',
              className: 'btn-show-chart',
              titleAttr: 'View chart',
              action: function ( e, dt, node, config ) {
                let onChartRendered = function(chartModel){
                    if ($(rootId).prop("is-setting-up")) return;
                    let currSection = wb.tabs[wb.currentTab].sections[rootId];
                    currSection.showChart = true;
                    currSection.chartModel = chartModel;
                    syncWork();
                };
                showChart(dsName, rootId, jsonContent, chartModel, onChartRendered);
              }
           }
       ],
       infoCallback: function( settings, start, end, max, total, pre ) {
           let duration = jsonContent.duration ? jsonContent.duration : 0;
           var info = "Query took " + duration + " sec(s). Showing " + start + " to " + end + " of " + total + " entries";
           return info
       },
       // Use this to properly align data with columns
       drawCallback: function( settings ) {
         // Reset margin to 0 after datatable render
         var ele = document.querySelector(elemId + '.dataTables_scrollBody');
         if(ele){
            ele = ele.querySelector(elemId);
            if(ele){
               ele.style.margin = 0;
            }
         }
       },
    } ).on( 'draw', function () {

       $(elemId + ' tbody tr').click(function(event) {
           $(this).addClass('row-selected').siblings().removeClass('row-selected');
       });
       $(elemId + ' tbody tr td').dblclick(function(){
           let rowData = extractRowData(table, $(this), columns);
           showRowDetailsDialog(rootId + "-rowdetails", dsName, rowData);
       });
    } );

   $(elemId + " .dt-buttons button").tooltip();

   $(elemId + ' tbody tr').click(function(event) {
       $(this).addClass('row-selected').siblings().removeClass('row-selected');
   });
   $(elemId + ' tbody tr td').dblclick(function(){
      let rowData = extractRowData(table, $(this), columns);
      showRowDetailsDialog(rootId + "-rowdetails", dsName, rowData);
   });

   let rowDetailsDialog = $("#rowDetailsTemplate .modal-row-details").clone();
   rowDetailsDialog.appendTo($(rootId));
   rowDetailsDialog.hide();
   let rowDetailsId = rootId.substring(1) + "-rowdetails";
   $('#' + rowDetailsId).remove();
   $(rootId + " .modal-row-details").attr('id', rowDetailsId);

   $(rootId + " .chart-container").remove();
   let overlayDiv = $("#chartDivTemplate .chart-container").clone();
   overlayDiv.appendTo($(rootId + "-collapse"));
   overlayDiv.hide();
   let chartSettingsId = rootId.substring(1) + "-chartsettings";
   $('#' + chartSettingsId).remove();
   $(rootId + " .modal-chart-settings").attr('id', chartSettingsId);
   $(rootId + ' .btn-view-table').click(function(){
       $(elemId + ' .dataTables_wrapper').show();
       $(rootId + ' .chart-container').hide();
   });
   $(rootId + ' .btn-chart-settings').click(function(){
       var chartSettingsDialog = $('#' + chartSettingsId).dialog({
           title: "Chart Settings - " + dsName,
           width: "600px",
           open: function(event, ui) {
               $(this).parent().css({'top': window.pageYOffset+60});
           }
        });
   });

   $(rootId + ' .btn-fullscreen').on('click', function(){
     if (document.fullscreenElement) {
       document.exitFullscreen();
     } else {
       $(rootId + ' .chart-container').get(0).requestFullscreen();
     }
   });
}

function getAllDatasets(rootDir) {

    $(".overlay").show();
    $.get( "/dataset/list", {path: rootDir}, function( data ) {
        $(".overlay").hide();
        treeData = data;
        navPane.updateNavPane(treeData);

    }).fail(function (xhr, status, error) {
        $(".overlay").hide();
        alert(error + "\n" + xhr.responseText);
    });
}

function getDataFromDataset(node, rootDir) {
    let currSection = wb.tabs[wb.currentTab].currentSection;
    $(currSection + " .description").text(node.text);
    $(currSection + " .acc-title").html(node.text);
    $(currSection + " .dataTables_wrapper").css("opacity", 0.3);
    loadingDiv.appendTo($(currSection + "-collapse"));
    $(currSection + " .msg-enter-sql").html(loadingDiv);
    $(currSection + " .loading").show();
    var startTime = Date.now();
    $.get( "/dataset/getDataFromTable",
        {"table" : node.text, "rootPath": rootDir},
        function(response) {
            response.query.duration = (Date.now() - startTime) / 1000.0;
            let sql = response.query.sql;
            $(currSection + " .search-box").val(sql);
            let currTab = wb.tabs[wb.currentTab];
            currTab.sections[currTab.currentSection] = {sectionId: currTab.currentSection,
                sectionName: node.text, description: node.path, sql : sql, queryId: response.queryId, showChart: false};
            addToSqlHistory(sql);
            renderDataTable(currSection, node.text, response.query);
            $(currSection + " .dataTables_wrapper").css("opacity", 1.0);
            $(currSection + " .loading").hide();
            syncWork();
    }).fail(function (xhr, status, error) {
        $(currSection + " .dataTables_wrapper").css("opacity", 1.0);
        $(currSection + " .loading").hide();
        alert(error + "\n" + xhr.responseText);
    });
}

function addToSqlHistory(sql) {
    if (sqlHistory.indexOf(sql) >= 0)
        sqlHistory.splice(sqlHistory.indexOf(sql), 1);
    sqlHistory.unshift(sql);
}

function setTableEventHandlers(tabId, elemId) {

    $(elemId + " .search-btn").click(function(){
        runQueryOnThisSection(tabId, elemId);
    });

    if (!jsEventsMinimize) {
        $(elemId + " .search-box").autocomplete({
          source: function(req, responseFn) {
              let re = $.ui.autocomplete.escapeRegex(req.term);
              let matcher = new RegExp("\\b" + re, "i" );
              let a = $.grep( sqlHistory, function(item,index){
                  return matcher.test(item);
              });
              responseFn( a );
          }
        });
    }

    $(elemId + " .search-box").on('keyup', function (e) {
        if (e.key === 'Enter' && e.ctrlKey) $(elemId + " .search-btn").trigger('click');
        else if (!jsEventsMinimize) {
            $(this).height( 'auto' );
            $(this).height( $(elemId + " .search-box")[0].scrollHeight );
        }
    });

    $(elemId + " .btn-delete-section").click(function () {
        let currTab = wb.tabs[wb.currentTab];
        let section = currTab.sections[elemId];
        if (!confirm("Are you sure you want to close this section?\n" + section.sectionName)) return;
        $(elemId).closest(".accordion-item").remove();
        delete currTab.sections[elemId];
        $(elemId + "-chartsettings").remove();
        currTab.sectionOrder.splice(currTab.sectionOrder.indexOf(elemId), 1);
        currTab.currentSection = (currTab.sectionOrder.length > 0) ? currTab.sectionOrder[0] : null;
    });

    $(elemId + " .btn-share-section").click(() => shareLink('query - '
      + wb.tabs[wb.currentTab].sections[elemId].sectionName, tabId, elemId, this) );

    $(elemId + " .btn-toggle-show-section").click(function(){
        $(elemId + "-collapse").collapse("toggle");
    });

    $(elemId).click(function(){
        setCurrentSection(elemId);
    });
}

function runCachedQueryOnThisSection(tabId, section) {
    let sectionId = section.sectionId;
    let queryId = section.queryId;
    if (queryId == null) return;
    $(sectionId + " .dataTables_wrapper").css("opacity", 0.3);
    loadingDiv.appendTo($(sectionId + "-collapse"));
    $(sectionId + " .loading").show();
    $.get( "/dataset/getCachedQuery", {"queryId" : queryId}, function(response) {
        if (response) {
            renderDataTable(sectionId, section.sectionName, response, section.chartModel);
            $(sectionId + " .dataTables_wrapper").css("opacity", 1.0);
            if (section.showChart) {
                $(section.sectionId + " .btn-show-chart").trigger('click');
            }
            $(section.sectionId).prop("is-setting-up", false);
        } else {
            console.log("Error: queryId " + queryId + " doesn't exist on the server.")
        }
        $(sectionId + " .loading").hide();
    }).fail(function (xhr, status, error) {
          $(sectionId + " .dataTables_wrapper").css("opacity", 1.0);
          $(sectionId + " .loading").hide();
          alert(error + "\n" + xhr.responseText);
      });
}

function runQueryOnThisSection(tabId, sectionId) {
    let sql = $(sectionId + " .search-box").val();
    if (sql.length == 0) return;
    let currTab = wb.tabs[tabId];
    let section = currTab.sections[sectionId];
    $(sectionId + " .dataTables_wrapper").css("opacity", 0.3);
    loadingDiv.appendTo($(sectionId + "-collapse"));
    $(sectionId + " .msg-enter-sql").html(loadingDiv);

    $(sectionId + " .loading").show();
    var startTime = Date.now();
    $.post( "/dataset/queryTable", {"sql" : sql}, function(response) {
        response.query.duration = (Date.now() - startTime) / 1000.0;
        renderDataTable(sectionId, section.sectionName, response.query);
        $(sectionId + " .dataTables_wrapper").css("opacity", 1.0);
        $(sectionId + " .loading").hide();
        section.sql = sql;
        section.showChart = false;
        section.queryId = response.queryId;
        addToSqlHistory(sql);
        syncWork();
    }).fail(function (xhr, status, error) {
          $(sectionId + " .dataTables_wrapper").css("opacity", 1.0);
          $(sectionId + " .loading").hide();
          alert(error + "\n" + xhr.responseText);
      });
}

function setCurrentSection(newSectionId) {
    let currTab = wb.tabs[wb.currentTab];
    currTab.currentSection = newSectionId;
    let sectionObj = $(newSectionId);
    sectionObj.addClass("acc-active");
    $(currTab.tabContentId + " .accordion-item").not(sectionObj).removeClass("acc-active");
}

function syncWork(callback) {
    $.post( "/dataset/syncWork", {"workbook" : JSON.stringify(wb)}, function(newWb) {
        wb = newWb;
        if (callback != null) {
            callback(wb);
        }
    }).fail(function (xhr, status, error) {
          alert(error + "\n" + xhr.responseText);
      });
}

function createTabs() {
    wb.tabOrder.map(t => {
        let tab = wb.tabs[t];
        createTabFromObj(tab);
        tab.sectionOrder.map(s => {
            let section = tab.sections[s];
            section.sectionId = s;
            createSectionFromObj(tab.tabId, tab.tabContentId, section);
        });
        if (t == wb.currentTab) {
            $(tab.currentSection).addClass("acc-active");
        }
        $(t).tab('show');
    });
    $(wb.currentTab).tab('show');

    Object.values(wb.tabs).map(t => {
        t.sectionOrder.map(s => {
            let section = t.sections[s];
            $(section.sectionId).prop("is-setting-up", true);
            runCachedQueryOnThisSection(t.tabId, section);
        });
        // Register this event only when all tabs are added
        $(t.tabId).on('shown.bs.tab', function (e) {
          wb.currentTab = t.tabId;
        });
    });
    $(".search-btn").tooltip();
    //Auto-resize textarea based on content: https://stackoverflow.com/a/13085420/3369952
    $(".search-box").each(function(textarea) {
        $(this).height( $(this)[0].scrollHeight );
    });
}

function createNewTab(tabCounter, isMasterTab) {

    let tab = { tabId: "#tab" + tabCounter,
        tabContentId: "#tab-content" + tabCounter,
        isMasterTab: isMasterTab,
        tabName: isMasterTab ? "Database Browser" : ("Tab #" + tabCounter),
        sectionOrder: [], currentSection: null, sections: {}
    };
    createTabFromObj(tab);
    $(tab.tabId).tab('show');
    wb.tabs[tab.tabId] = tab;
    wb.currentTab = tab.tabId;
    wb.tabOrder.push(tab.tabId);
    wb.sectionCounter++;
    createNewSection(wb.currentTab, wb.sectionCounter);
}

function createTabFromObj(tab) {
    let tabId = tab.tabId.substring(1);
    let tabContentId = tab.tabContentId.substring(1);
    let tabHeadClone = $("#newTabTemplate .ui-tabs-tab").clone();
    $('.nav-tabs #btn-add-tab').before(tabHeadClone);
    tabHeadClone.find(".nav-link").attr("id", tabId).attr("data-bs-target", "#" + tabContentId);
    tabHeadClone.find(".tab-name").text(tab.tabName);
    let tabName = $(tab.tabId + " .tab-name");
    let editTabName = $(tab.tabId + " .edit-tab-name");
    if (tab.isMasterTab) {
        tabHeadClone.find(".btn-close").hide();
    }
    if (!tab.isMasterTab) {
        tabName.dblclick(function(){
            tabName.hide();
            editTabName.val(tabName.text());
            editTabName.show();
            editTabName.focus();
            syncWork();
        });
    }
    editTabName.keyup(function(e){
        if (e.key === 'Enter') {
            tabName.text(editTabName.val());
            wb.tabs[tab.tabId].tabName = editTabName.val();
            editTabName.blur();
            syncWork();
        } else if (e.key === 'Escape') {
            editTabName.blur();
        }
    });
    editTabName.blur(function(){
        tabName.show();
        editTabName.hide();
    });
    let tabClone = $("#newTabTemplate .tab-pane").clone();
    $('.tab-content').append(tabClone);
    tabClone.attr("id", tabContentId).attr("aria-labelledby", tabId);
    $(tab.tabContentId + " .btn-new-section").click(function(){
        wb.sectionCounter++;
        createNewSection(tab.tabId, wb.sectionCounter);
    });
    $(tab.tabContentId + " .accordion" ).sortable({
        handle: ".accordion-header",
        axis: "y",
        containment: '#' + tabContentId + " .accordion",
        tolerance: "pointer",
        cursor: "move",
        opacity: 0.5,
        update: function(event, ui) {
            var arrayOfIds = $.map($('#' + tabContentId + " .accordion-item"), function(n, i){
              return '#' + n.id;
            });
            wb.tabs[wb.currentTab].sectionOrder = arrayOfIds;
            setCurrentSection('#' + $(ui.item[0]).attr('id'));
            syncWork();
        }
    });
    $(tab.tabId + " .closeTab").click(function(){
        if (!confirm("Are you sure you want to close this tab? " + tabName.text())) return;
        $(tab.tabId).parent().remove();
        $('#' + tabContentId).remove();
        wb.tabs[tab.tabId].sectionOrder.map(sId => $(sId + "-chartsettings").remove());
        delete wb.tabs[tab.tabId];
        wb.tabOrder.splice(wb.tabOrder.indexOf(tab.tabId), 1);
        $(wb.tabOrder[0]).tab('show');
        syncWork();
    });
}

function createNewSection(tabId, sectionCounter) {

    let sectionId = "#acc-section" + sectionCounter;
    let section = {sectionId: sectionId,
        sectionName:"Query #" + sectionCounter,
        description: '', sql: ''};
    let tab = wb.tabs[tabId];
    createSectionFromObj(tabId, tab.tabContentId, section);
    tab.sections[sectionId] = section;
    tab.sectionOrder.push(sectionId);
    setCurrentSection(sectionId);
    $(sectionId).find(".search-btn").tooltip();
}

function createSectionFromObj(tabId, tabContentId, section) {

    let accSectionId = section.sectionId.substring(1);
    let headerId = accSectionId + "-header";
    let contentId = accSectionId + "-collapse";

    let sectionClone = $("#newSectionTemplate .accordion-item").clone();
    $(tabContentId + " .accordion").append(sectionClone);
    sectionClone.attr("id", accSectionId);
    sectionClone.find(".accordion-header").attr("id", headerId);
    sectionClone.find(".acc-toggle-btn").attr("data-bs-target", "#" + contentId).attr("aria-controls", contentId);
    let secTitle = sectionClone.find(".acc-title"); secTitle.html(section.sectionName);
    let editSecTitle = sectionClone.find(".edit-acc-title"); editSecTitle.val(section.sectionName);
    sectionClone.find(".accordion-collapse").attr("id", contentId).attr("aria-labelledby", headerId);
    sectionClone.find(".description").text(section.description);
    sectionClone.find(".search-box").val(section.sql);
    setTableEventHandlers(tabId, section.sectionId);

    secTitle.dblclick(function(){
        secTitle.hide();
        editSecTitle.val(secTitle.text());
        editSecTitle.show();
        editSecTitle.focus();
        syncWork();
    });
    editSecTitle.keyup(function(e){
        if (e.key === 'Enter') {
            secTitle.text(editSecTitle.val());
            wb.tabs[tabId].sections[section.sectionId].sectionName = editSecTitle.val();
            editSecTitle.blur();
            syncWork();
        } else if (e.key === 'Escape') {
            editSecTitle.blur();
        }
    });
    editSecTitle.blur(function(){
        secTitle.show();
        editSecTitle.hide();
    });
}

function showNewDatasetModal() {
   $("#new-dataset-path").val('');
   $('.modal-new-dataset').dialog({
        title: "New dataset",
        modal: true,
        width: 500,
        height: 220,
        buttons: {
            Close: function() {
              $( this ).dialog( "close" );
            }
        }
     });
}

function shareLink(type, tabId, sectionId, elem) {

    let loading = $(".modal-share-link .loading"); loading.show();
    let inputGroup = $(".modal-share-link .input-group"); inputGroup.hide();
    $.get( "/dataset/getShareLink", {tabId: tabId, sectionId: sectionId}, function( data ) {
        loading.hide();
        inputGroup.show();
        inputGroup.find("#link-box").val(location.origin + location.pathname + "/share?sid=" + data.shareId);
    }).fail(function (xhr, status, error) {
        alert(error + "\n" + xhr.responseText);
    });
   $('.modal-share-link').dialog({
        title: "Share this " + type,
        width: 500,
        height: 200,
        buttons: {
            Close: function() {
              $( this ).dialog( "close" );
            }
        }
     });
}