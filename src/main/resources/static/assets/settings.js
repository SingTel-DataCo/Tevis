
$(function() {
    $.fn.bootstrapBtn = $.fn.button.noConflict();

    $('#btn-save-changes').click(function(){

        let oldPass = $("#input-old-password").val();
        let newPass = $("#input-new-password").val();
        let newPass2 = $("#input-confirm-password").val();
        if (newPass !== newPass2) {
            alert("New passwords don't match.");
        } else {
            $.post( "/settings/updatePassword", {oldPassword: oldPass, newPassword: newPass},
                function(content) {
                    alert("Password successfully updated.");
            }).fail(function (xhr, status, error) {
                $(".overlay").hide();
                alert(error + "\n" + xhr.responseText);
            });
        }
    });
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
        $('.dataTable table').DataTable().ajax.reload();
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
        $('.dataTable table').DataTable().ajax.reload();
    }).fail(function (xhr, status, error) {
        $(".overlay").hide();
        alert(error + "\n" + xhr.responseText);
    });
}

function showUserDialog(isEditMode, userData) {
    $("#input-username").attr('disabled', isEditMode);
    $("#input-username").val(userData.username);
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
                $('.dataTable table').DataTable().ajax.reload();
            }).fail(function (xhr, status, error) {
                $(".overlay").hide();
                alert(error + "\n" + xhr.responseText);
            });
        }
    });
}