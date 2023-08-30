var chartImageUri = null;
var elemIdFuncDrawChartMap = {};

function hasOverflow(element) {
    return (element.offsetHeight < element.scrollHeight
            || element.offsetWidth < element.scrollWidth);
}

function showRowDetailsDialog(rowDetailsId, title, content) {
    var options = {
         collapsed: true,
         rootCollapsable: false,
         withQuotes: true,
         withLinks: false
    };
    try {
       $(rowDetailsId + ' .json-viewer').show();
       $(rowDetailsId + ' .text-viewer').hide();
       $(rowDetailsId + ' .json-viewer').jsonViewer(JSON.parse(content), options);
       $(rowDetailsId + ' .text-viewer').val(JSON.stringify(JSON.parse(content), null, 4));
    } catch (e) {
       $(rowDetailsId + ' .text-viewer').show();
       $(rowDetailsId + ' .json-viewer').hide();
    }
    $(rowDetailsId).parent().find(".toggle-json-view").val('JSON'); //reset the select field to JSON
    $(rowDetailsId).dialog({
        title: "Row Details - " + title,
        width: 500,
        height: 500,
        open: function(event, ui) {
            $(this).parent().css({'top': window.pageYOffset+60});
        },
        buttons: {
            Close: function() {
              $( this ).dialog( "close" );
            }
        },
        create: function() {
              var menuHTML = "";
              menuHTML += "<div class='btn-group' style='padding-top: 5px'>";
              menuHTML += '<select class="form-select toggle-json-view ui-widget">'
                                     +  "<option value='JSON'>JSON Viewer</option>"
                                     +  "<option value='Text'>Text Box</option></select>";
              menuHTML += "</div>";
              $(menuHTML).insertBefore($(this).closest(".ui-dialog").find(".ui-dialog-buttonset"));
            }
     });

     $(rowDetailsId).parent().find(".toggle-json-view").change(function(){
         if($(this).val() == 'JSON') {
           $(rowDetailsId + ' .json-viewer').show();
           $(rowDetailsId + ' .text-viewer').hide();
         } else {
           $(rowDetailsId + ' .json-viewer').hide();
           $(rowDetailsId + ' .text-viewer').show();
         }
     });
}

function extractRowData(table, tblRow, colNames) {
    return JSON.stringify(extractRowDataJson(table, tblRow, colNames));
}

function extractRowDataJson(table, tblRow, colNames) {
    var data = table.row(tblRow.parents('tr')).data();
    var result = {};
    colNames.forEach((key, i) => {
        var value = data[i];
        try {
            value = JSON.parse(data[i]);
        } catch {}
      result[key] = value; });
    return result;
}

function showChart(dsName, rootId, jsonContent, chartModel, eventCallback) {
    $(rootId + ' .dataTables_wrapper').hide();
    $(rootId + ' .chart-container').show();
    if (!$(rootId + ' .chart-container').prop("is-chart-displayed")) {
        let ds = {"name": dsName, "schema": jsonContent.schema, "data": jsonContent.data};
        initChartSettings(rootId, ds, chartModel, eventCallback);
        $(rootId + ' .chart-container').prop("is-chart-displayed", "true");
    }
}

function initChartSettings(elemId, ds, chartModel, chartRenderedCallback) {
    let chartSettingsId = elemId + '-chartsettings';
    let sortableUiList = $(chartSettingsId + " .sortable");
    sortableUiList.empty();
    Object.keys(ds.schema).forEach(function(k){
        sortableUiList.append($('<li class="ui-state-default"><span class="ui-icon ui-icon-arrowthick-2-n-s"></span><label><input name="chart-columns" type="checkbox" checked value="' + k + '" />' + k + '</label></li>'));
    });
    if (chartModel != null) {
        $(chartSettingsId + ' input[name="chart-columns"]').prop('checked', false);
        chartModel.selectedColumns.forEach(k => $(chartSettingsId + ' input[value="' + k + '"]').prop('checked', true));
        $(chartSettingsId + " .chart-type").val(chartModel.chartType);
        $(chartSettingsId + " .textbox-chart-options").val(JSON.stringify(chartModel.extraOptions));
    }
    var mapBase = null, map = null;
    let rerenderChart = function() {
        var chartModel = getChartModel(elemId);
        if ($(chartSettingsId +' .chart-type').val() == "GeoJSONMap") {
            $(elemId + " .map").show();
            $(elemId + " .dynamic-chart").hide();
            if (map == null) {
                mapBase = initMapSettings(elemId);
                map = mapBase[1];
                mapBase = mapBase[0];
            }
            map.eachLayer(function (layer) {
                map.removeLayer(layer);
            });
            let dsData = ds.data.map(function(d){ return chartModel.selectedColumns.map(function(k, i){
                return i == 0 ? JSON.parse(d[k]) : (d[k] + "") }) });
            dsData.map(function(r) {
                let geojsonLayer = L.geoJson(r[0], {
                    coordsToLatLng: function (coords) {
                        return new L.LatLng(coords[0], coords[1], coords[2]);
                    },
                    style: function (feature) {
                        return {color: random_rgba()};
                    }
                }).bindPopup(function (layer) {
                    return r.length > 1 ? r[1] : null;
                }).addTo(map);
            });
            mapBase.fitBounds(map.getBounds());
        } else {
            $(elemId + " .map").hide();
            $(elemId + " .dynamic-chart").show();
            let dsData = ds.data.map(function(d){ return chartModel.selectedColumns.map(function(k){
                return typeof d[k] === "object" ? JSON.stringify(d[k]) : (k.toLowerCase().includes("date") ? new Date(d[k]) : d[k]) }) });
            dsData.unshift(chartModel.selectedColumns);
            renderChart(ds, dsData, elemId, chartModel);
        }
        if (chartRenderedCallback) chartRenderedCallback(chartModel);
    };

    $(chartSettingsId + ' .btn-apply-chart-options').click(rerenderChart);
    $(chartSettingsId + ' .chart-type').change(rerenderChart);
    sortableUiList.sortable({ update: rerenderChart });
    $(chartSettingsId + ' input[name="chart-columns"]').change(rerenderChart);
    $(chartSettingsId + ' .btn-check-all').change(function(){
        let toggleCheckAll = $(this).prop('checked');
        $(chartSettingsId + ' input[name="chart-columns"]').prop('checked', toggleCheckAll);
        rerenderChart();
    });

    $(".leafletjs-info").tooltip();

    $(elemId + " .dt-buttons button").tooltip();
    rerenderChart();
}

function initMapSettings(elemId) {

    var map = L.map(document.querySelector(elemId + ' .map')).setView([0.0, 0.0], 2);
    //More leaflet base providers here: https://leaflet-extras.github.io/leaflet-providers/preview/
//    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
//        maxZoom: 19,
//        attribution: '&copy; <a href="http://www.openstreetmap.org/copyright">OpenStreetMap</a>'
//    }).addTo(map);
//
//    var Esri_WorldGrayCanvas = L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Light_Gray_Base/MapServer/tile/{z}/{y}/{x}', {
//    	attribution: 'Tiles &copy; Esri &mdash; Esri, DeLorme, NAVTEQ',
//    	maxZoom: 16
//    }).addTo(map);
    var CartoDB_Positron = L.tileLayer('https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png', {
    	attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/attributions">CARTO</a>',
    	subdomains: 'abcd',
    	maxZoom: 20
    }).addTo(map);
    var featureGroup = L.featureGroup().addTo(map);
    return [map, featureGroup];
}

function renderChart(ds, dsData, elemId, chartModel) {

    google.charts.load('current', {'packages':['corechart']}).then(function () {

       var data = google.visualization.arrayToDataTable(dsData);
        var options = {
          theme: 'maximized',
          title: ds.name,
          legend: { position: 'bottom' },
          hAxis: {
            slantedText:true,
            slantedTextAngle:45,
            textStyle: {
              fontSize : 10
            }
          },
          chartArea: {
            height: '100%',
            width: '100%',
            top: 48,
            left: 48,
            right: 16,
            bottom: 48
          },
          height: '100%',
          width: '100%',
        };
        $.extend(options, chartModel.extraOptions);

        var chart = new google.visualization.ChartWrapper({
            chartType: chartModel.chartType,
            dataTable: data,
            options: options,
            containerId: document.querySelector(elemId + ' .dynamic-chart')
        });

        // Disabled for now due to poor image resolution as compared to getting a screenshot.
        //  google.visualization.events.addListener(chart, 'ready', function () {
        //        chartImageUri = chart.getChart().getImageURI();
        //    });
        drawChart();
        elemIdFuncDrawChartMap[elemId] = drawChart;
        function drawChart() {
            chart.draw();
        }
    });
}

//debounce() Source: https://stackoverflow.com/a/45905199/3369952
function debounce(func){
  var timer;
  return function(event){
    if(timer) clearTimeout(timer);
    timer = setTimeout(func,100,event);
  };
}

$(function() {
    window.addEventListener('resize', debounce(tryDrawChart), false);
    $("#mySidebar").on("resize", debounce(tryDrawChart));
});

function getChartModel(elemId) {
    let chartSettingsId = elemId + '-chartsettings';
    let selectedCols = $(chartSettingsId + ' input[name="chart-columns"]:checked')
    .map(function(){ return $(this).val(); }).get();
    var extraOptions = {};
    try {
        extraOptions = JSON.parse($(chartSettingsId + " .textbox-chart-options").val());
    } catch (e) {
        console.log("Error parsing JSON from 'Extra options' textbox: " + e);
    }
    return {
       chartType: $(chartSettingsId + ' .chart-type').val(),
       selectedColumns: selectedCols,
       extraOptions: extraOptions
    };
}

function getChartExtraOptions(chartSettingsId) {

    try {
        return JSON.parse($(chartSettingsId + " .textbox-chart-options").val());
    } catch (e) {
        console.log("Error parsing JSON from 'Extra options' textbox: " + e);
        return {};
    }
}

function tryDrawChart() {
    Object.values(elemIdFuncDrawChartMap).forEach(drawFunc => drawFunc());
}

function random_rgba() {
    var o = Math.round, r = Math.random, s = 255;
    return 'rgba(' + o(r()*s) + ',' + o(r()*s) + ',' + o(r()*s) + ',' + 1 + ')';
}

//Source: https://stackoverflow.com/a/5723274/3369952
let truncate = function (fullStr, strLen, separator) {
    if (fullStr.length <= strLen) return fullStr;

    separator = separator || '...';

    var sepLen = separator.length,
        charsToShow = strLen - sepLen,
        frontChars = Math.ceil(charsToShow/2),
        backChars = Math.floor(charsToShow/2);

    return fullStr.substr(0, frontChars) +
           separator +
           fullStr.substr(fullStr.length - backChars);
};

// For module config tables and dataset schemas
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