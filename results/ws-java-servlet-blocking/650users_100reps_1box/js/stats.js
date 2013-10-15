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
        "total": "65000",
        "ok": "59729",
        "ko": "5271"
    },
    "minResponseTime": {
        "total": "190",
        "ok": "190",
        "ko": "7010"
    },
    "maxResponseTime": {
        "total": "63030",
        "ok": "56620",
        "ko": "63030"
    },
    "meanResponseTime": {
        "total": "1809",
        "ok": "1211",
        "ko": "8584"
    },
    "standardDeviation": {
        "total": "2042",
        "ok": "1258",
        "ko": "0"
    },
    "percentiles1": {
        "total": "7010",
        "ok": "2050",
        "ko": "16140"
    },
    "percentiles2": {
        "total": "10140",
        "ok": "5210",
        "ko": "54140"
    },
    "group1": {
        "name": "t < 800 ms",
        "count": 3849,
        "percentage": 5
    },
    "group2": {
        "name": "800 ms < t < 1200 ms",
        "count": 51171,
        "percentage": 78
    },
    "group3": {
        "name": "t > 1200 ms",
        "count": 4709,
        "percentage": 7
    },
    "group4": {
        "name": "failed",
        "count": 5271,
        "percentage": 8
    },
    "meanNumberOfRequestsPerSecond": {
        "total": "307",
        "ok": "282",
        "ko": "25"
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
        "total": "65000",
        "ok": "59729",
        "ko": "5271"
    },
    "minResponseTime": {
        "total": "190",
        "ok": "190",
        "ko": "7010"
    },
    "maxResponseTime": {
        "total": "63030",
        "ok": "56620",
        "ko": "63030"
    },
    "meanResponseTime": {
        "total": "1809",
        "ok": "1211",
        "ko": "8584"
    },
    "standardDeviation": {
        "total": "2042",
        "ok": "1258",
        "ko": "0"
    },
    "percentiles1": {
        "total": "7010",
        "ok": "2050",
        "ko": "16140"
    },
    "percentiles2": {
        "total": "10140",
        "ok": "5210",
        "ko": "54140"
    },
    "group1": {
        "name": "t < 800 ms",
        "count": 3849,
        "percentage": 5
    },
    "group2": {
        "name": "800 ms < t < 1200 ms",
        "count": 51171,
        "percentage": 78
    },
    "group3": {
        "name": "t > 1200 ms",
        "count": 4709,
        "percentage": 7
    },
    "group4": {
        "name": "failed",
        "count": 5271,
        "percentage": 8
    },
    "meanNumberOfRequestsPerSecond": {
        "total": "307",
        "ok": "282",
        "ko": "25"
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
