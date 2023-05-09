class NavigationPane {

    initialize() {
        let btnActions = $("#file-list-actions");
        $("#file-list-actions button").tooltip()
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
                    navPane.updateNavPane(data);

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
                    navPane.updateNavPane(treeData);

                }).fail(function (xhr, status, error) {
                    $(".overlay").hide();
                    alert(error + "\n" + xhr.responseText);
                });
            }
        });
        btnActions.tooltip();
        $("#file-list-filter").keyup(function() {
            let textToSearch = $(this).val().toLowerCase();
            let filteredTreeData = navPane.filterTreeData(treeData, textToSearch);
            navPane.updateNavPane(filteredTreeData, true);
        });

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

    updateNavPane(tmpData, expand) {
        $("#file-list-actions-hideout").append($("#file-list-actions"));
        let rnFileGroupNav = tmpData.map((rn, i) => {
            let collapseId = 'collapse' + i;
            let rnBadge = ' <span class="badge">' + rn.tags[0] + '</span>';
            let rnBtnToggle = '<span class="btn-toggle rounded" style="padding-bottom: 10px" data-bs-toggle="collapse" data-bs-target="#'
             + collapseId + '" ><span class="file-group-name" data-bs-toggle="popover" title="' + rn.text + '" data-bs-content="<div class=\'btn-actions\'></div>Path: ' + rn.path + '">' + truncate(rn.text, 45) + '</span>' + rnBadge + '</span>';
            let liList = rn.nodes.map(cn => {
                return '<li class="file-item rounded"><i class="fa fa-table" style="color: #999"></i> <span class="filename-label" data-bs-toggle="popover" title="' + cn.text + '" data-bs-content="<div class=\'btn-actions\'></div>Path: ' + cn.path + '" path="' + cn.path + '">' + cn.text + '</span></li>';
            }).join('');
            let show = expand ? " show" : "";
            let hideRootNode = rn.tags[0] == 0 ? " d-none" : "";
            let collapseDiv = '<div class="collapse ' + show + '" id="' + collapseId + '">'
                + '<ul class="btn-toggle-nav list-unstyled">'
                + liList + '</ul></div>';
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
                    $('.btn-read').show(); $('.btn-unmount').hide();
                    $('.btn-refresh').attr('data-bs-original-title', "Refresh dataset");
                 } else {
                    $('.btn-read').hide(); $('.btn-unmount').show();
                    $('.btn-refresh').attr('data-bs-original-title', "Refresh collection");
                 }
                 let popoverId = $(this).attr("aria-describedby");
                 $('#' + popoverId).find('.btn-actions').append(btnActions);
             });
    }

    filterTreeData(data, textToSearch) {
        return data.map(function(rNode){
            let cNodes = rNode.nodes.filter(cn => cn.text.toLowerCase().includes(textToSearch.toLowerCase()));
            let rNodeClone = $.extend(true, {}, rNode);
            rNodeClone.nodes = cNodes;
            rNodeClone.tags = [cNodes.length];
            return rNodeClone;
        });
    }
}