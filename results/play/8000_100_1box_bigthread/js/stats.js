var stats = {
    type: "GROUP",
contents: {
"my-page-2462060507643742b646e4c5a20268f5": {
        type: "REQUEST",
        name: "My Page",
path: "My Page",
pathFormatted: "my-page-2462060507643742b646e4c5a20268f5",
stats: {
    "name": "My Page",
    "numberOfRequests": {
        "total": "800000",
        "ok": "800000",
        "ko": "0"
    },
    "minResponseTime": {
        "total": "190",
        "ok": "190",
        "ko": "-"
    },
    "maxResponseTime": {
        "total": "39030",
        "ok": "39030",
        "ko": "-"
    },
    "meanResponseTime": {
        "total": "7486",
        "ok": "7486",
        "ko": "-"
    },
    "standardDeviation": {
        "total": "4673",
        "ok": "4673",
        "ko": "-"
    },
    "percentiles1": {
        "total": "16570",
        "ok": "16570",
        "ko": "-"
    },
    "percentiles2": {
        "total": "22360",
        "ok": "22360",
        "ko": "-"
    },
    "group1": {
        "name": "t < 800 ms",
        "count": 15322,
        "percentage": 1
    },
    "group2": {
        "name": "800 ms < t < 1200 ms",
        "count": 11852,
        "percentage": 1
    },
    "group3": {
        "name": "t > 1200 ms",
        "count": 772826,
        "percentage": 96
    },
    "group4": {
        "name": "failed",
        "count": 0,
        "percentage": 0
    },
    "meanNumberOfRequestsPerSecond": {
        "total": "878",
        "ok": "878",
        "ko": "-"
    }
}
    }
},
name: "Global Information",
path: "",
pathFormatted: "missing-name-b06d1db11321396efb70c5c483b11923",
stats: {
    "name": "Global Information",
    "numberOfRequests": {
        "total": "800000",
        "ok": "800000",
        "ko": "0"
    },
    "minResponseTime": {
        "total": "190",
        "ok": "190",
        "ko": "-"
    },
    "maxResponseTime": {
        "total": "39030",
        "ok": "39030",
        "ko": "-"
    },
    "meanResponseTime": {
        "total": "7486",
        "ok": "7486",
        "ko": "-"
    },
    "standardDeviation": {
        "total": "4673",
        "ok": "4673",
        "ko": "-"
    },
    "percentiles1": {
        "total": "16570",
        "ok": "16570",
        "ko": "-"
    },
    "percentiles2": {
        "total": "22360",
        "ok": "22360",
        "ko": "-"
    },
    "group1": {
        "name": "t < 800 ms",
        "count": 15322,
        "percentage": 1
    },
    "group2": {
        "name": "800 ms < t < 1200 ms",
        "count": 11852,
        "percentage": 1
    },
    "group3": {
        "name": "t > 1200 ms",
        "count": 772826,
        "percentage": 96
    },
    "group4": {
        "name": "failed",
        "count": 0,
        "percentage": 0
    },
    "meanNumberOfRequestsPerSecond": {
        "total": "878",
        "ok": "878",
        "ko": "-"
    }
}

}

function fillStats(stat){
    $("#numberOfRequests").append(stat.numberOfRequests.total);
    $("#numberOfRequestsOK").append(stat.numberOfRequests.ok);
    $("#numberOfRequestsKO").append(stat.numberOfRequests.ko);

    $("#minResponseTime").append(stat.minResponseTime.total);
    $("#minResponseTimeOK").append(stat.minResponseTime.ok);
    $("#minResponseTimeKO").append(stat.minResponseTime.ko);

    $("#maxResponseTime").append(stat.maxResponseTime.total);
    $("#maxResponseTimeOK").append(stat.maxResponseTime.ok);
    $("#maxResponseTimeKO").append(stat.maxResponseTime.ko);

    $("#meanResponseTime").append(stat.meanResponseTime.total);
    $("#meanResponseTimeOK").append(stat.meanResponseTime.ok);
    $("#meanResponseTimeKO").append(stat.meanResponseTime.ko);

    $("#standardDeviation").append(stat.standardDeviation.total);
    $("#standardDeviationOK").append(stat.standardDeviation.ok);
    $("#standardDeviationKO").append(stat.standardDeviation.ko);

    $("#percentiles1").append(stat.percentiles1.total);
    $("#percentiles1OK").append(stat.percentiles1.ok);
    $("#percentiles1KO").append(stat.percentiles1.ko);

    $("#percentiles2").append(stat.percentiles2.total);
    $("#percentiles2OK").append(stat.percentiles2.ok);
    $("#percentiles2KO").append(stat.percentiles2.ko);

    $("#meanNumberOfRequestsPerSecond").append(stat.meanNumberOfRequestsPerSecond.total);
    $("#meanNumberOfRequestsPerSecondOK").append(stat.meanNumberOfRequestsPerSecond.ok);
    $("#meanNumberOfRequestsPerSecondKO").append(stat.meanNumberOfRequestsPerSecond.ko);
}
