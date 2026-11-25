export default function () {
    var form = document.getElementById('filter-form');
    var range = form.querySelector('[data-role=range]');
    var dates = form.querySelector('[data-role=dates]');
    range.addEventListener('change', function () {
        if (range.value === 'custom') {
            dates.hidden = false;
            dates.querySelector('input').focus();
        } else {
            if (form.requestSubmit) form.requestSubmit(); else form.submit();
        }
    });
}
