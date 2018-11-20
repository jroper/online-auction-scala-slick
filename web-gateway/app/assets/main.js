$().ready(function () {
    $(".log-in-as").each(function() {
        var logInAs = $(this);
        var userId = logInAs.data("user-id");
        var csrfToken = logInAs.data("csrf-token");
        logInAs.click(function() {
            $.ajax({
                type: "POST",
                url: "/currentuser/" + userId,
                headers: {
                    "CSRF-Token" : csrfToken
                }
            }).then(function() {
                // Force reload as a GET. If an ordinary reload was done, then for pages loaded by POST,
                // the POST will be resubmitted containing the old CSRF token, however, this won't work,
                // since after logging in, we would have a new session with a new CSRF token.
                var loc = window.location;
                window.location = loc.protocol + '//' + loc.host + loc.pathname + loc.search;
            });
        });
    });
});
$(document).foundation();
