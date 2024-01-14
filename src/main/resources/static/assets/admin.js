let dsColumns = [
    { title: "username" },
    { title: "role" },
    { title: "dateCreated" },
    { title: "lastLogin" },
    {
        data: null,
        className: "btn btn-sm dt-center btn-edit-user",
        defaultContent: '<i class="fa fa-pencil"/>',
        orderable: false
    },
    {
        data: null,
        className: "btn btn-sm dt-center btn-delete-user",
        defaultContent: '<i class="fa fa-trash"/>',
        orderable: false
    }
];
let columns = dsColumns.map(x => x.title).filter(x => x !== undefined);

$(function() {
    $.fn.bootstrapBtn = $.fn.button.noConflict();
    $.get( "/admin/getUsers",
        function(content) {
            let table = $("#table-users table").DataTable( {
               dom: 'Bfrtip',
               pageLength: 20,
               columns: dsColumns,
               ajax: '/admin/getUsers',
               buttons: [
                  {
                     text: "<i class='fa fa-user'></i> <span>New User</span>",
                     action: function ( e, dt, node, config ) {
                        let rowDataEmpty = {username: "", role: "USER"};
                        showUserDialog(false, rowDataEmpty);
                     }
                  },
                  {
                      text: "<i class='fa fa-refresh'></i> <span>Refresh</span>",
                      action: function (e, dt, node, config) {
                          dt.ajax.reload(null, false);
                      }
                  }
               ],
            } ).on( 'draw', function () {
              registerRowActions(table);
              $('#table-users tbody tr').click(function(event) {
                  $(this).addClass('row-selected').siblings().removeClass('row-selected');
              });
              $('#table-users tbody tr td').dblclick(function(){
                  let rowData = extractRowDataJson(table, $(this), columns);
                  showUserDialog(true, rowData);
              });
           } );

    }).fail(function (xhr, status, error) {
        $(".overlay").hide();
        alert(error + "\n" + xhr.responseText);
    });

    $('#btn-stop-spark').click(function(){
        $(".overlay").show();
        $.post( "/admin/stopSpark",
            function(content) {
                alert("Spark session has been stopped.");
                $(".overlay").hide();
        }).fail(function (xhr, status, error) {
            $(".overlay").hide();
            alert(error + "\n" + xhr.responseText);
        });
    });

    $('#btn-restart-spark').click(function(){
        $(".overlay").show();
        $.post( "/admin/restartSpark",
            function(content) {
                alert("Spark session has been restarted.");
                $(".overlay").hide();
        }).fail(function (xhr, status, error) {
            $(".overlay").hide();
            alert(error + "\n" + xhr.responseText);
        });
    });

    $('#btn-purge-past-queries').click(function(){
        $(".overlay").show();
        $.post( "/admin/purgePastQueries",
            function(content) {
                alert(content + " past queries purged successfully.");
                $(".overlay").hide();
        }).fail(function (xhr, status, error) {
            $(".overlay").hide();
            alert(error + "\n" + xhr.responseText);
        });
    });

    let tableSpark = $("#table-spark-configs table").DataTable( {
        autoWidth: false,
       dom: 'Bfrtip',
       pageLength: 30,
       columns: [{title: "name", width: "30%"}, {title: "type", width: "10%"}, {title: "value"}],
       ajax: '/admin/getAppEnv',
       buttons: [
          {
              text: "<i class='fa fa-refresh'></i> <span>Refresh</span>",
              action: function (e, dt, node, config) {
                  dt.ajax.reload(null, false);
              }
          }
       ],
    } ).on( 'draw', function () {
      $('#table-spark-configs tbody tr').click(function(event) {
          $(this).addClass('row-selected').siblings().removeClass('row-selected');
      });
   } );

});

function ajaxCreateUser(){
    var roles = $('input[name="check-role"]:checked').map(function(){
                  return $(this).val();
            }).get().join(",");
    $.post( "/admin/createUser", {
            username: $("#input-username").val(),
            password: $("#input-password").val(),
            roles: roles
        },
        function(content) {
        $('#table-users table').DataTable().ajax.reload();
    }).fail(function (xhr, status, error) {
        $(".overlay").hide();
        alert(error + "\n" + xhr.responseText);
    });
}

function ajaxModifyUser(){
    var roles = $('input[name="check-role"]:checked').map(function(){
                  return $(this).val();
            }).get().join(",");
    $.post( "/admin/modifyUser", {
            username: $("#input-username").val(),
            password: $("#input-password").val(),
            roles: roles
        },
        function(content) {
        $('#table-users table').DataTable().ajax.reload();
    }).fail(function (xhr, status, error) {
        $(".overlay").hide();
        alert(error + "\n" + xhr.responseText);
    });
}

function showUserDialog(isEditMode, userData) {
    $("#input-username").attr('disabled', isEditMode);
    $("#input-username").val(userData.username);
    $("#input-password").val("");
    $('input[name="check-role"]').prop('checked', false);
    userData.role.split(",").forEach(k => $('input[value="' + k.trim() + '"]').prop('checked', true));
    $('.modal-user').dialog({
        title: isEditMode ? ("Edit user - " + userData.username) : "Create new user",
        width: "600px",
        open: function(event, ui) {
            $(this).parent().css({'top': window.pageYOffset+60});
        },
        buttons: {
          "Save changes": function() {
             if (isEditMode) {
                ajaxModifyUser();
             } else {
                ajaxCreateUser();
             }
            $( this ).dialog( "close" );
          },
          Cancel: function() {
            $( this ).dialog( "close" );
          }
        }
     });
}

function registerRowActions(table) {

    $('.btn-edit-user').click(function(){
        let tblRow = $(this).closest("td");
        let rowData = extractRowDataJson(table, tblRow, columns);
        showUserDialog(true, rowData);
    });

    $('.btn-delete-user').click(function(){
        let tblRow = $(this).closest("td");
        let rowData = extractRowDataJson(table, tblRow, columns);
        if (confirm("Are you sure you want to delete this user?\n" + rowData.username)) {
            $.post( "/admin/deleteUser", {
                    username: rowData.username
                },
                function(content) {
                $('#table-users table').DataTable().ajax.reload();
            }).fail(function (xhr, status, error) {
                $(".overlay").hide();
                alert(error + "\n" + xhr.responseText);
            });
        }
    });
}