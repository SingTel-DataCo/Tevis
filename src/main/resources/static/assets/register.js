var loadingDiv = "<div class='loading lds-ellipsis'><div></div><div></div><div></div><div></div></div>";

$(function() {

    $("#form-create-account").submit(function(e) {
        e.preventDefault();
        $(".div-error-msg").html(loadingDiv);

        var paramObj = {};
        $.each($('#form-create-account').serializeArray(), function(_, kv) {
          if (paramObj.hasOwnProperty(kv.name)) {
            paramObj[kv.name] = $.makeArray(paramObj[kv.name]);
            paramObj[kv.name].push(kv.value);
          }
          else {
            paramObj[kv.name] = kv.value;
          }
        });
        if (paramObj.password[0] != paramObj.password[1]) {
            $(".div-error-msg").html($("<div class='alert alert-danger'>Passwords don't match.</div>"));
            return false;
        } else {
            paramObj.password = paramObj.password[0];
        }

        $.post( e.currentTarget.action, paramObj,
            function(content) {
                $.post("/login", paramObj, function() {
                    window.location = "/"
                });
        }).fail(function (xhr, status, error) {
            $(".overlay").hide();
            let msg = xhr.responseText.substring(xhr.responseText.indexOf(":") + 1);
            $(".div-error-msg").html($("<div class='alert alert-danger'>" + msg + "</div>"));
        });
     });
});