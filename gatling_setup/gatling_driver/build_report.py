import sys
import math

rampTime = 10
runTime = 50
numLoops = 10
firstLine = True
startTime = 0
startSend = 4
endSend = 5
startReceive = 6
endReceive = 7
outCome = 8
maxIndex = 9
currentIndex = -1
okCount = 0
koCount = 0
latencyList=[]
usersPerRamp = 20
latencies=[10, 25, 50, 75, 90, 95, 99]


def getIndex(requestTime):
    timeSinceStart = requestTime - startTime
    if (timeSinceStart > 0):
        stepTime = (rampTime+runTime)*1000
        index = timeSinceStart/stepTime
        if ((timeSinceStart % stepTime) < 1000*rampTime):
            return -1
        if (index <= maxIndex):
            return index
    return -1

def printIndexResults():
    global okCount
    global koCount
    global latencyList
    latencyList.sort()
    latencyResults = []
    time = runTime*1000.0
    for pct in latencies:
        latencyListLen = len(latencyList)
        idx = int(pct*latencyListLen/100)
        latencyResults.append(latencyList[idx])
    if (currentIndex >= 0):
        print("%(idx)s\t%(ok)s\t%(ko)s\t%(l)s" % 
            {'ok': okCount/time, 'ko': koCount/time, 'idx' : (currentIndex+1)*usersPerRamp, 'l' : str(latencyResults) })
    okCount = 0
    koCount = 0
    latencyList=[]


for line in sys.stdin:
   elements = line.split()
   if len(elements) >= 9:
      if(firstLine): 
        firstLine=False
        startTime=int(elements[startSend])
      thisIndex= getIndex(int(elements[startSend]))
      if (thisIndex < 0):
        continue
      if (elements[outCome] == 'OK'):
         okCount = okCount+1
      else:
         koCount = koCount+1
      latency = int(elements[endReceive]) - int(elements[startSend])
      latencyList.append(latency)
      if (thisIndex != currentIndex):
         printIndexResults()
         
         currentIndex = thisIndex
         
printIndexResults()
         
