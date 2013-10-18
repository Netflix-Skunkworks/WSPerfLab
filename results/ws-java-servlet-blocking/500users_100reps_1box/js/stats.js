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
        "total": "50000",
        "ok": "47534",
        "ko": "2466"
    },
    "minResponseTime": {
        "total": "190",
        "ok": "190",
        "ko": "7010"
    },
    "maxResponseTime": {
        "total": "63010",
        "ok": "59180",
        "ko": "63010"
    },
    "meanResponseTime": {
        "total": "1465",
        "ok": "1145",
        "ko": "7625"
    },
    "standardDeviation": {
        "total": "1555",
        "ok": "1189",
        "ko": "0"
    },
    "percentiles1": {
        "total": "7010",
        "ok": "1980",
        "ko": "7020"
    },
    "percentiles2": {
        "total": "7020",
        "ok": "4260",
        "ko": "52820"
    },
    "group1": {
        "name": "t < 800 ms",
        "count": 4525,
        "percentage": 9
    },
    "group2": {
        "name": "800 ms < t < 1200 ms",
        "count": 40115,
        "percentage": 80
    },
    "group3": {
        "name": "t > 1200 ms",
        "count": 2894,
        "percentage": 5
    },
    "group4": {
        "name": "failed",
        "count": 2466,
        "percentage": 4
    },
    "meanNumberOfRequestsPerSecond": {
        "total": "282",
        "ok": "268",
        "ko": "14"
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
        "total": "50000",
        "ok": "47534",
        "ko": "2466"
    },
    "minResponseTime": {
        "total": "190",
        "ok": "190",
        "ko": "7010"
    },
    "maxResponseTime": {
        "total": "63010",
        "ok": "59180",
        "ko": "63010"
    },
    "meanResponseTime": {
        "total": "1465",
        "ok": "1145",
        "ko": "7625"
    },
    "standardDeviation": {
        "total": "1555",
        "ok": "1189",
        "ko": "0"
    },
    "percentiles1": {
        "total": "7010",
        "ok": "1980",
        "ko": "7020"
    },
    "percentiles2": {
        "total": "7020",
        "ok": "4260",
        "ko": "52820"
    },
    "group1": {
        "name": "t < 800 ms",
        "count": 4525,
        "percentage": 9
    },
    "group2": {
        "name": "800 ms < t < 1200 ms",
        "count": 40115,
        "percentage": 80
    },
    "group3": {
        "name": "t > 1200 ms",
        "count": 2894,
        "percentage": 5
    },
    "group4": {
        "name": "failed",
        "count": 2466,
        "percentage": 4
    },
    "meanNumberOfRequestsPerSecond": {
        "total": "282",
        "ok": "268",
        "ko": "14"
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
