
$(function() {
    $.fn.bootstrapBtn = $.fn.button.noConflict();

    let htmlColorMode = $("html").attr("data-bs-theme");
    $('input:radio[name=radio-color-mode][value=' + htmlColorMode + ']').prop('checked', true);
    $('input:radio[name=radio-color-mode]').change(function(){
        $("html").attr("data-bs-theme", this.value);
    });

    $('#btn-save-settings').click(function(){
        $(".overlay").show();
        let colorMode = $('input:radio[name=radio-color-mode]:checked').val();
        $.post( "/settings/updateSettings", {colorMode: colorMode},
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