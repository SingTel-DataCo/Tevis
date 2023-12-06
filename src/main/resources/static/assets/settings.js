
$(function() {
    $.fn.bootstrapBtn = $.fn.button.noConflict();

    $('#toggle-dark-mode').change(function(){
        let darkMode = $(this).prop('checked');
        $("body").attr("data-bs-theme", darkMode ? "dark" : "light");
    });

    $('#btn-save-settings').click(function(){
        $(".overlay").show();
        let darkMode = $("#toggle-dark-mode").prop('checked');

        $.post( "/settings/updateSettings", {darkMode: darkMode},
            function(content) {
                alert("Settings successfully updated.");
        }).fail(function (xhr, status, error) {
            $(".overlay").hide();
            alert(error + "\n" + xhr.responseText);
        });

    });

    $('#btn-update-password').click(function(){
        $(".overlay").show();
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