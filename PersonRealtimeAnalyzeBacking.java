package com.sophlean.rmes.web.backing.quality;

import com.sophlean.core.ctx.security.PermissionManager;
import com.sophlean.core.ctx.web.Page;
import com.sophlean.core.mq.MQPublisher;
import com.sophlean.core.mq.util.WebPushEndpoint;
import com.sophlean.core.util.DecimalUtils;
import com.sophlean.core.util.ListUtils;
import com.sophlean.core.util.SpringUtils;
import com.sophlean.core.util.StringUtils;
import com.sophlean.core.util.chart.CartesianChartModel;
import com.sophlean.core.util.chart.ChartSeries;
import com.sophlean.core.util.log.Log;
import com.sophlean.rmes.entity.HardWareMessage;
import com.sophlean.rmes.entity.event.AbnormalEvent;
import com.sophlean.rmes.entity.event.EventType;
import com.sophlean.rmes.entity.mi.KpiStatusCode;
import com.sophlean.rmes.entity.personnel.Person;
import com.sophlean.rmes.entity.personnel.PersonPositioningData;
import com.sophlean.rmes.service.event.AbnormalEventService;
import com.sophlean.rmes.service.event.notification.Notifier;
import com.sophlean.rmes.service.event.notification.WebPushNotifier;
import com.sophlean.rmes.service.personnel.PersonService;
import com.sophlean.rmes.service.scm.EquipmentPositioningAreaService;
import com.sophlean.rmes.service.scm.PersonPositionDataService;
import com.sophlean.rmes.web.LayoutManager;
import com.sophlean.rmes.web.backing.AbstractBacking;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_17;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.context.annotation.Scope;

import javax.annotation.Resource;
import javax.inject.Named;
import java.io.*;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.*;

@Named("personRealtimeAnalyzeBacking")
@Scope("session")
public class PersonRealtimeAnalyzeBacking extends AbstractBacking {
    private static final long serialVersionUID = 615615615L;

    protected static final Log log = Log.getLog(PersonRealtimeAnalyzeBacking.class);
    private String serverIp = "192.168.1.11";
    private String protocol = "localSensePush-protocol";
    private WebSocketClient wc;

    private static final String CHANNELTIME = "/locationTime";

    // TODO 连接真实数据
    private float agv1Distance = 641;
    // TODO 连接真实数据
    private float agv2Distance = 215;
    private int agv1Id = 11248;
    private int agv1LastX;
    private int agv1LastY;
    private int agv2Id = 11225;
    private int agv2LastX;
    private int agv2LastY;
    private boolean first1 = true;
    private boolean first2 = true;

    private Map<String, List<LocationPos>> timeMaps = new HashMap();

    private double rate = 0.1;
    private String x = "x";

    private List<String> personList = new ArrayList<>();
    private List<String> toPersonList = new ArrayList<>();

    private boolean connected = false;
    private boolean trail = false;

    private String[] person1;
    private String[] person2;
    private String[] person3;
    private String[] person4;

    @Resource
    LayoutManager layoutManager;
    double heatRate;

    @Named("personRealtimeAnalyzeBackingPages")
    @Scope("singleton")
    public static class Pages {
        public static final Page PAGE = new Page(
                "page.personRealtimeKpiAnalyze",
                "/views/quality/personRealtimeAnalyze.xhtml",
                "#{personRealtimeAnalyzeBacking.gotoPage()}",
                PermissionManager.FAST_RESPONSE_VIEW);
    }

    public PersonRealtimeAnalyzeBacking() {
        super(Pages.PAGE);
        try {
            File file1 = SpringUtils.getFile("/conf/location/10547.txt");
            BufferedReader in = new BufferedReader(new FileReader(file1));
            String line;
            String[] temp = {};
            while((line = in.readLine()) != null) {
                temp = line.split(x);
            }
            in.close();
            for(int i = 0; i < temp.length; ++i) {
                temp[i] = "1," + temp[i];
            }
            person1 = temp;

            File file2 = SpringUtils.getFile("/conf/location/10557.txt");
            BufferedReader in2 = new BufferedReader(new FileReader(file2));
            String line2;
            String[] temp2 = {};
            while((line2 = in2.readLine()) != null) {
                temp2 = line2.split(x);
            }
            in2.close();
            for(int i = 0; i < temp2.length; ++i) {
                temp2[i] = "2," + temp2[i];
            }
            person2 = temp2;

            File file3 = SpringUtils.getFile("/conf/location/10808.txt");
            BufferedReader in3 = new BufferedReader(new FileReader(file3));
            String line3;
            String[] temp3 = {};
            while((line3 = in3.readLine()) != null) {
                temp3 = line3.split(x);
            }
            in3.close();
            for(int i = 0; i < temp3.length; ++i) {
                temp3[i] = "3," + temp3[i];
            }
            person3 = temp3;

            File file4 = SpringUtils.getFile("/conf/location/11227.txt");
            BufferedReader in4 = new BufferedReader(new FileReader(file4));
            String line4;
            String[] temp4 = {};
            while((line4 = in4.readLine()) != null) {
                temp4 = line4.split(x);
            }
            in4.close();
            for(int i = 0; i < temp4.length; ++i) {
                temp4[i] = "4," + temp4[i];
            }
            person4 = temp4;
        }
        catch (FileNotFoundException e) {
            log.error(e.getMessage());
        }
        catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    @Override
    protected void doReset() {
        heatRate = ((layoutManager.getBodyHeight() * 2 / 3 - 82) * 2.8)/946;
        toPersonList.add("111");
        toPersonList.add("222");
        toPersonList.add("333");
        toPersonList.add("444");
        {
            CurLocationInfo ci1 = new CurLocationInfo();
            ci1.setPersonDbid(Long.parseLong(person1[1].split(",")[0]));
            ci1.setX(((Long.parseLong(person1[1].split(",")[1]) - 190)/2) * heatRate);
            ci1.setY((338 - (Long.parseLong(person1[1].split(",")[2]) - 75)/2) * heatRate);
            ci1.setPosTime(new Date(new Date().getTime()));
            simInfo.add(ci1);

            CurLocationInfo ci2 = new CurLocationInfo();
            ci2.setPersonDbid(Long.parseLong(person2[1].split(",")[0]));
            ci2.setX(((Long.parseLong(person2[1].split(",")[1]) - 190)/2) * heatRate);
            ci2.setY((338 - (Long.parseLong(person2[1].split(",")[2]) - 60)/2) * heatRate);
            ci2.setPosTime(new Date(new Date().getTime()));
            simInfo.add(ci2);

            CurLocationInfo ci3 = new CurLocationInfo();
            ci3.setPersonDbid(Long.parseLong(person3[1].split(",")[0]));
            ci3.setX(((Long.parseLong(person3[1].split(",")[1]) - 890)/2) * heatRate);
            ci3.setY((338 - (Long.parseLong(person3[1].split(",")[2]) - 52)/2) * heatRate);
            ci3.setPosTime(new Date(new Date().getTime()));
            simInfo.add(ci3);

            CurLocationInfo ci4 = new CurLocationInfo();
            ci4.setPersonDbid(Long.parseLong(person4[1].split(",")[0]));
            ci4.setX(((Long.parseLong(person4[1].split(",")[1]) - 270)/2) * heatRate);
            ci4.setY((338 - (Long.parseLong(person4[1].split(",")[2]) - 100)/2) * heatRate);
            ci4.setPosTime(new Date(new Date().getTime()));
            simInfo.add(ci4);
        }
        this.heatmapChartModel = this.createHeatmapChartModel();
    }

    private List<Person> realtimePersonList;
    private Person realtimeManager;

    public List<Person> getRealtimePersonList() {
        // TODO 连接真实数据
        if (this.realtimePersonList == null) {
            this.realtimePersonList = ListUtils.newArrayList();
            this.realtimePersonList.add(this.personService.findOneByBzid("O003"));
            this.realtimePersonList.add(this.personService.findOneByBzid("O004"));
            this.realtimePersonList.add(this.personService.findOneByBzid("O002"));
            this.realtimePersonList.add(this.personService.findOneByBzid("S001"));
            this.realtimePersonList.add(this.personService.findOneByBzid("S002"));
            this.realtimePersonList.add(this.personService.findOneByBzid("S003"));
            this.realtimePersonList.add(this.personService.findOneByBzid("S004"));
        }
        return realtimePersonList;
    }

    public Person getRealtimeManager() {
        // TODO 连接真实数据
        if (this.realtimeManager == null) {
            this.realtimeManager = this.personService.findOneByBzid("O001");
        }
        return realtimeManager;
    }

    private List<PersonRanking> personRankingList;

    public List<PersonRanking> getPersonRankingList() {
        if (this.personRankingList == null) {
            this.personRankingList = ListUtils.newArrayList();
            this.personRankingList.add(new PersonRanking(1, "李光洁"));
            this.personRankingList.add(new PersonRanking(2, "周同远"));
            this.personRankingList.add(new PersonRanking(3, "周志敏"));
            this.personRankingList.add(new PersonRanking(4, "张峰"));
            this.personRankingList.add(new PersonRanking(5, "潘松"));
            this.personRankingList.add(new PersonRanking(6, "蔡润泽"));
            this.personRankingList.add(new PersonRanking(7, "陈卓文"));
            this.personRankingList.add(new PersonRanking(8, "刘斌"));
            this.personRankingList.add(new PersonRanking(9, "杨新宇"));
            this.personRankingList.add(new PersonRanking(10, "薛文桥"));
            this.personRankingList.add(new PersonRanking(11, "孙连鑫"));
            this.personRankingList.add(new PersonRanking(12, "李伟"));
            this.personRankingList.add(new PersonRanking(13, "刘思彤"));
            this.personRankingList.add(new PersonRanking(14, "张峰铭"));
            this.personRankingList.add(new PersonRanking(15, "王国川"));
        }
        return this.personRankingList;
    }

    public static class PersonRanking {

        public PersonRanking(int ranking, String personName) {
            this.ranking = ranking;
            this.personName = personName;
        }

        private int ranking;
        private String personName;

        public int getRanking() {
            return ranking;
        }

        public String getPersonName() {
            return personName;
        }
    }

    @Resource
    PersonPositionDataService personPositionDataService;
    @Resource
    EquipmentPositioningAreaService equipmentPositioningAreaService;

    public String getServerIp() {
        return serverIp;
    }

    public void setServerIp(String serverIp) {
        this.serverIp = serverIp;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    @Resource
    AbnormalEventService abnormalEventService;
    @Resource
    PersonService personService;

    public void connect() {
        if(!connected) {
            try {
//                File file1 = new File("d:\\9959.txt");
//                FileWriter out1 = new FileWriter(file1);
//                File file2 = new File("d:\\10527.txt");
//                FileWriter out2 = new FileWriter(file2);
//                File file3 = new File("d:\\10547.txt");
//                FileWriter out3 = new FileWriter(file3);
//                File file4 = new File("d:\\10557.txt");
//                FileWriter out4 = new FileWriter(file4);
//                File file5 = new File("d:\\10559.txt");
//                FileWriter out5 = new FileWriter(file5);
//                File file6 = new File("d:\\10808.txt");
//                FileWriter out6 = new FileWriter(file6);
//                File file7 = new File("d:\\11227.txt");
//                FileWriter out7 = new FileWriter(file7);
//                List<String> p1 = new ArrayList<>();
//                List<String> p2 = new ArrayList<>();
//                List<String> p3 = new ArrayList<>();
//                List<String> p4 = new ArrayList<>();
//                List<String> p5 = new ArrayList<>();
//                List<String> p6 = new ArrayList<>();
//                List<String> p7 = new ArrayList<>();
                List<Long> rec = new ArrayList<>();
                Map<String, String> headers = new HashMap<>();
                headers.put("Sec-WebSocket-Protocol", protocol);
                wc = new WebSocketClient(new URI("ws://" + serverIp + ":9001"), new Draft_17(), headers, 1000000) {
                    String curPositionId = null;

                    @Override
                    public void onOpen(ServerHandshake handshakedata) {
                        log.info("Location Connected");
                        connected = true;
                    }

                    @Override
                    public void onMessage(String message) {
                        log.info("String message:" + message);
                    }

                    @Override
                    public void onMessage(ByteBuffer bytes) {
                        int length = bytes.remaining();
                        byte[] bs = new byte[length];
                        bytes.get(bs);
                        StringBuffer result = new StringBuffer();
                        String hex;
                        for (int i = 0; i < bs.length; i++) {
                            hex = Integer.toHexString(bs[i] & 0xFF);
                            if (hex.length() == 1) {
                                hex = '0' + hex;
                            }
                            result.append(hex.toUpperCase());
                        }

                        if (StringUtils.isNotBlank(result)) {
                            switch (result.substring(0, 6)) {
                                case "CC5F01":
                                    //实时位置信息
                                    int tagNum = Integer.parseInt(result.substring(6, 8), 16);
                                    int offset = 42;
                                    for (int i = 0; i < tagNum; ++i) {
                                        String tag = result.substring(8 + i * 42, 8 + (i + 1) * 42);
                                        int tagId = Integer.parseInt(tag.substring(0, 4), 16);
                                        int x = Integer.parseInt(tag.substring(4, 12), 16);
                                        int y = Integer.parseInt(tag.substring(12, 20), 16);
//                                        try {
//                                            if(tagId == 9959) {
//                                                p1.add(x + "," + y);
//                                                if(p1.size() == 800) {
//                                                    for(int k = 0; k < p1.size(); ++k) {
//                                                        out1.write(p1.get(k) + "x");
//                                                    }
//                                                    out1.close();
//                                                }
//                                            }
//                                            else if(tagId == 10527) {
//                                                p2.add(x + "," + y);
//                                                if(p2.size() == 800) {
//                                                    for(int k = 0; k < p2.size(); ++k) {
//                                                        out2.write(p2.get(k) + "x");
//                                                    }
//                                                    out2.close();
//                                                }
//                                            }
//                                            else if(tagId == 10547) {
//                                                p3.add(x + "," + y);
//                                                if(p3.size() == 800) {
//                                                    for(int k = 0; k < p3.size(); ++k) {
//                                                        out3.write(p3.get(k) + "x");
//                                                    }
//                                                    out3.close();
//                                                }
//                                            }
//                                            else if(tagId == 10557) {
//                                                p4.add(x + "," + y);
//                                                if(p4.size() == 800) {
//                                                    for(int k = 0; k < p4.size(); ++k) {
//                                                        out4.write(p4.get(k) + "x");
//                                                    }
//                                                    out4.close();
//                                                }
//                                            }
//                                            else if(tagId == 10559) {
//                                                p5.add(x + "," + y);
//                                                if(p5.size() == 800) {
//                                                    for(int k = 0; k < p5.size(); ++k) {
//                                                        out5.write(p5.get(k) + "x");
//                                                    }
//                                                    out5.close();
//                                                }
//                                            }
//                                            else if(tagId == 10808) {
//                                                p6.add(x + "," + y);
//                                                if(p6.size() == 800) {
//                                                    for(int k = 0; k < p6.size(); ++k) {
//                                                        out6.write(p6.get(k) + "x");
//                                                    }
//                                                    out6.close();
//                                                }
//                                            }
//                                            else if(tagId == 11227) {
//                                                p7.add(x + "," + y);
//                                                if(p7.size() == 800) {
//                                                    for(int k = 0; k < p7.size(); ++k) {
//                                                        out7.write(p7.get(k) + "x");
//                                                    }
//                                                    out7.close();
//                                                }
//                                            }
//                                        }
//                                        catch (IOException e) {
//                                            System.out.println(e.getMessage());
//                                        }

                                        if (tagId == agv1Id) {
                                            if (first1) {
                                                agv1Distance = 0;
                                                agv1LastX = x;
                                                agv1LastY = y;
                                                first1 = false;
                                            } else {
                                                agv1Distance += Math.hypot(x - agv1LastX, y - agv1LastY) / 100;
                                                agv1LastX = x;
                                                agv1LastY = y;
                                            }
                                        } else if (tagId == agv2Id) {
                                            if (first2) {
                                                agv2Distance = 0;
                                                agv2LastX = x;
                                                agv2LastY = y;
                                                first2 = false;
                                            } else {
                                                agv2Distance += Math.hypot(x - agv2LastX, y - agv2LastY) / 100;
                                                agv2LastX = x;
                                                agv2LastY = y;
                                            }
                                        } else {
                                            WebPushEndpoint.push(CHANNELTIME, "1," + tagId + "," + x + "," + y);
                                            if(trail) {
                                                if(timeMaps.get(tagId + "") != null) {
                                                    List<LocationPos> pos = timeMaps.get(tagId + "");
                                                    LocationPos p = new LocationPos();
                                                    p.setX(x + "");
                                                    p.setY(y + "");
                                                    pos.add(p);
                                                    timeMaps.put(tagId + "", pos);
                                                }
                                                else {
                                                    List<LocationPos> pos = new ArrayList<>();
                                                    LocationPos p = new LocationPos();
                                                    p.setX(x + "");
                                                    p.setY(y + "");
                                                    pos.add(p);
                                                    timeMaps.put(tagId + "", pos);
                                                }
                                                List<LocationPos> posList = timeMaps.get(tagId + "");
                                                if(posList.size() > 1) {
                                                    String trailInfo = "3," + tagId + ".";
                                                    for (int j = 0; j < posList.size(); ++j) {
                                                        trailInfo += posList.get(j).getX() + "," + posList.get(j).getY() + ".";
                                                    }
                                                    WebPushEndpoint.push(CHANNELTIME, trailInfo);
                                                }
                                            }
                                        }
                                    }
                                    break;
                                case "CC5F03":
                                    //报警信息
                                    log.info("报警");
                                    rec.add(1L);
                                    Long alarmType = Long.parseLong(result.substring(6, 8), 16);
                                    String eventType;
                                    //电子围栏报警
                                    if (alarmType == 1) {
                                        String alarmInfo = result.substring(28, result.length() - 8);
                                        String str = "0123456789ABCDEF";
                                        char[] hexs = alarmInfo.toCharArray();
                                        byte[] infoByte = new byte[alarmInfo.length() / 2];
                                        int n;
                                        for (int i = 0; i < infoByte.length; i++) {
                                            n = str.indexOf(hexs[2 * i]) * 16;
                                            n += str.indexOf(hexs[2 * i + 1]);
                                            infoByte[i] = (byte) (n & 0xff);
                                        }
                                        try {
                                            String position = new String(infoByte, "GB2312");

                                            Long tagId = Long.parseLong(result.substring(8, 12), 16);

                                            if (position.split("\"")[0].equals(tagId + " 进入围栏：")) {
                                                log.info("进入围栏报警");
                                                eventType = "enter";
                                                String[] temp = position.split("\"");
                                                String positionId = temp[1];
                                                if (curPositionId == null) {
                                                    curPositionId = positionId;
                                                }

                                                Date date = new Date(Long.parseLong(result.substring(12, 28), 16));

                                                Long EquipmentDBId = equipmentPositioningAreaService.getEquipmentDBidByAreaBzid(positionId).getEquipmentDbid();
                                                Long personDbid = personService.findOneByIndoorPositioningTagId(Long.toString(tagId)).getDbid();

                                                PersonPositioningData data = new PersonPositioningData();
                                                data.setPersonDbid(personDbid);
                                                data.setEquipmentDbid(EquipmentDBId);
                                                data.setPositioningAreaBzid(positionId);
                                                data.setTransactionTime(date);
                                                data.setEventType(EventType.ADDITION);
                                                personPositionDataService.insert(data);

                                                MQPublisher mqPublisher = new MQPublisher("indoorLocation");
                                                List<HardWareMessage> messageList = new ArrayList<>();
                                                HardWareMessage message = new HardWareMessage();
                                                message.setType("areaA");
                                                message.setTime(date);
                                                message.setEquipment(EquipmentDBId + "");
                                                message.setLocation(positionId);
                                                message.setPerson(personDbid + "");
                                                message.setEvent(eventType);
                                                messageList.add(message);
                                                mqPublisher.publish(messageList);

                                                if(positionId.equals("offLine")) {
                                                    String inInfo = "2," + tagId + "," + personService.findNameByTagId(Long.toString(tagId)) + "," + positionId + ",enter";
                                                    WebPushEndpoint.push(CHANNELTIME, inInfo);
                                                }

                                            } else if (position.split("\"")[1].equals(tagId + "")) {
                                                log.info("出围栏报警");
                                                eventType = "exit";
                                                String positionId = null;
                                                if (curPositionId != null) {
                                                    positionId = curPositionId;
                                                    curPositionId = null;
                                                }

                                                Date date = new Date(Long.parseLong(result.substring(12, 28), 16));

                                                Long EquipmentDBId = equipmentPositioningAreaService.getEquipmentDBidByAreaBzid(positionId).getEquipmentDbid();
                                                Long personDbid = personService.findOneByIndoorPositioningTagId(Long.toString(tagId)).getDbid();

                                                PersonPositioningData data = new PersonPositioningData();
                                                data.setPersonDbid(personDbid);
                                                data.setPositioningAreaBzid(positionId);
                                                data.setTransactionTime(date);
                                                data.setEventType(EventType.DELETION);
                                                personPositionDataService.insert(data);

                                                MQPublisher mqPublisher = new MQPublisher("indoorLocation");
                                                List<HardWareMessage> messageList = new ArrayList<>();
                                                HardWareMessage message = new HardWareMessage();
                                                message.setType("areaA");
                                                message.setTime(date);
                                                message.setEquipment(EquipmentDBId + "");
                                                message.setLocation(positionId);
                                                message.setPerson(personDbid + "");
                                                message.setEvent(eventType);
                                                messageList.add(message);
                                                mqPublisher.publish(messageList);
                                                if(positionId.equals("offLine")) {
                                                    String outInfo = "2," + tagId + "," + personService.findNameByTagId(Long.toString(tagId)) + "," + positionId + ",exit";
                                                    WebPushEndpoint.push(CHANNELTIME, outInfo);
                                                }
                                            }

                                        } catch (Exception e) {
                                            log.error(e.getMessage());
                                        }
                                    } else if (alarmType == 2) {
                                        eventType = "sos";
                                        log.info("SOS报警");
                                        String tagId = Long.parseLong(result.substring(8, 12), 16) + "";
                                        String personName = personService.findOneByIndoorPositioningTagId(tagId).getName();
                                        Date date = new Date(Long.parseLong(result.substring(12, 28), 16));
                                        AbnormalEvent event = new AbnormalEvent();
                                        event.setTagId(tagId);
                                        event.setPersonName(personName);
                                        event.setSendTime(date);
                                        event.setIssue(personName + " SOS报警");
                                        List<Notifier> notifierList = new ArrayList<>();
                                        for(Long personDbid : rec) {
                                            notifierList.add(new WebPushNotifier(personDbid));
                                        }
                                        abnormalEventService.saveAndNotify(event, notifierList);
                                    } else if (alarmType == 7) {
                                        log.info("低电量报警");
                                        String tagId = Long.parseLong(result.substring(8, 12), 16) + "";
                                        String personName = personService.findOneByIndoorPositioningTagId(tagId).getName();
                                        Date date = new Date(Long.parseLong(result.substring(12, 28), 16));
                                        AbnormalEvent event = new AbnormalEvent();
                                        event.setTagId(tagId);
                                        event.setPersonName(personName);
                                        event.setSendTime(date);
                                        event.setIssue(personName + "低电量报警");
                                        List<Notifier> notifierList = new ArrayList<>();
                                        for(Long personDbid : rec) {
                                            notifierList.add(new WebPushNotifier(personDbid));
                                        }
                                        abnormalEventService.saveAndNotify(event, notifierList);
                                    }
                                    break;
                                case "CC5F05":
                                    //电量信息
                                    Long personDbIdEle = Long.parseLong(result.substring(8, 12), 16);
                                    break;
                                default:
                                    //扩展信息
                                    Long personDbIdMore = Long.parseLong(result.substring(8, 12), 16);
                                    break;
                            }
                        }
                    }

                    @Override
                    public void onClose(int code, String reason, boolean remote) {
                        log.info("closed");
                        connected = false;
                    }

                    @Override
                    public void onError(Exception ex) {
                        log.error("Error:->>" + ex.getMessage());
                        connected = false;
                    }
                };
                wc.connect();
            } catch (Exception e) {
                connected = false;
                log.error(e.getMessage());
            }
        }
    }

    public void testCNC() {
        MQPublisher mqPublisher = new MQPublisher("indoorLocation");
        List<HardWareMessage> messageList = new ArrayList<>();
        HardWareMessage message = new HardWareMessage();
        message.setType("areaA");
        message.setTime(new Date());
        message.setEquipment("5");
        message.setLocation("CNC");
        message.setPerson("1");
        message.setEvent("enter");
        messageList.add(message);
        mqPublisher.publish(messageList);
    }

    public String distance(String name) {
        if (name.equals("AGV-1")) {
            return agv1Distance + "";
        } else if (name.equals("AGV-2")) {
            return agv2Distance + "";
        }
        return 0 + "";
    }

    public KpiStatusCode getAgvBatteryStatusCode(String name) {
        if (name.equals("AGV-1")) {
            double agv1LastBattery = 100 - agv1Distance * rate;
            if (DecimalUtils.compare(BigDecimal.valueOf(agv1LastBattery), BigDecimal.valueOf(75)) >= 0) {
                return KpiStatusCode.NORMAL;
            } else if (DecimalUtils.compare(BigDecimal.valueOf(agv1LastBattery), BigDecimal.valueOf(75)) < 0
                    && DecimalUtils.compare(BigDecimal.valueOf(agv1LastBattery), BigDecimal.valueOf(50)) >= 0) {
                return KpiStatusCode.NORMAL;
            } else if (DecimalUtils.compare(BigDecimal.valueOf(agv1LastBattery), BigDecimal.valueOf(50)) < 0
                    && DecimalUtils.compare(BigDecimal.valueOf(agv1LastBattery), BigDecimal.valueOf(25)) >= 0) {
                return KpiStatusCode.WARNING;
            } else if (DecimalUtils.compare(BigDecimal.valueOf(agv1LastBattery), BigDecimal.valueOf(25)) < 0
                    && DecimalUtils.compare(BigDecimal.valueOf(agv1LastBattery), BigDecimal.valueOf(10)) >= 0) {
                return KpiStatusCode.WARNING;
            } else {
                return KpiStatusCode.DANGER;
            }
        } else if (name.equals("AGV-2")) {
            double agv2LastBattery = 100 - agv2Distance * rate;
            if (DecimalUtils.compare(BigDecimal.valueOf(agv2LastBattery), BigDecimal.valueOf(75)) >= 0) {
                return KpiStatusCode.NORMAL;
            } else if (DecimalUtils.compare(BigDecimal.valueOf(agv2LastBattery), BigDecimal.valueOf(75)) < 0
                    && DecimalUtils.compare(BigDecimal.valueOf(agv2LastBattery), BigDecimal.valueOf(50)) >= 0) {
                return KpiStatusCode.NORMAL;
            } else if (DecimalUtils.compare(BigDecimal.valueOf(agv2LastBattery), BigDecimal.valueOf(50)) < 0
                    && DecimalUtils.compare(BigDecimal.valueOf(agv2LastBattery), BigDecimal.valueOf(25)) >= 0) {
                return KpiStatusCode.WARNING;
            } else if (DecimalUtils.compare(BigDecimal.valueOf(agv2LastBattery), BigDecimal.valueOf(25)) < 0
                    && DecimalUtils.compare(BigDecimal.valueOf(agv2LastBattery), BigDecimal.valueOf(10)) >= 0) {
                return KpiStatusCode.WARNING;
            } else {
                return KpiStatusCode.DANGER;
            }
        }
        return null;
    }

    public String getAgvBatteryIconStyleClass(String name) {
        if (name.equals("AGV-1")) {
            double agv1LastBattery = 100 - agv1Distance * rate;
            if (DecimalUtils.compare(BigDecimal.valueOf(agv1LastBattery), BigDecimal.valueOf(75)) >= 0) {
                return "fa fa-battery-full text-green";
            } else if (DecimalUtils.compare(BigDecimal.valueOf(agv1LastBattery), BigDecimal.valueOf(75)) < 0
                    && DecimalUtils.compare(BigDecimal.valueOf(agv1LastBattery), BigDecimal.valueOf(50)) >= 0) {
                return "fa fa-battery-three-quarters text-green";
            } else if (DecimalUtils.compare(BigDecimal.valueOf(agv1LastBattery), BigDecimal.valueOf(50)) < 0
                    && DecimalUtils.compare(BigDecimal.valueOf(agv1LastBattery), BigDecimal.valueOf(25)) >= 0) {
                return "fa fa-battery-half text-yellow";
            } else if (DecimalUtils.compare(BigDecimal.valueOf(agv1LastBattery), BigDecimal.valueOf(25)) < 0
                    && DecimalUtils.compare(BigDecimal.valueOf(agv1LastBattery), BigDecimal.valueOf(10)) >= 0) {
                return "fa fa-battery-quarter text-yellow";
            } else {
                return "fa fa-battery-empty text-red";
            }
        } else if (name.equals("AGV-2")) {
            double agv2LastBattery = 100 - agv2Distance * rate;
            if (DecimalUtils.compare(BigDecimal.valueOf(agv2LastBattery), BigDecimal.valueOf(75)) >= 0) {
                return "fa fa-battery-full text-green";
            } else if (DecimalUtils.compare(BigDecimal.valueOf(agv2LastBattery), BigDecimal.valueOf(75)) < 0
                    && DecimalUtils.compare(BigDecimal.valueOf(agv2LastBattery), BigDecimal.valueOf(50)) >= 0) {
                return "fa fa-battery-three-quarters text-green";
            } else if (DecimalUtils.compare(BigDecimal.valueOf(agv2LastBattery), BigDecimal.valueOf(50)) < 0
                    && DecimalUtils.compare(BigDecimal.valueOf(agv2LastBattery), BigDecimal.valueOf(25)) >= 0) {
                return "fa fa-battery-half text-yellow";
            } else if (DecimalUtils.compare(BigDecimal.valueOf(agv2LastBattery), BigDecimal.valueOf(25)) < 0
                    && DecimalUtils.compare(BigDecimal.valueOf(agv2LastBattery), BigDecimal.valueOf(10)) >= 0) {
                return "fa fa-battery-quarter text-yellow";
            } else {
                return "fa fa-battery-empty text-red";
            }
        }
        return "";
    }

    private static final String CHANNELSIM = "/locationSim";
    private volatile boolean isConnect = false;

    public void testWebSocket() {
        if (!isConnect)
            isConnect = true;
        else
            isConnect = false;
        new Thread() {
            public void run() {
                int i = 0;
                while (isConnect) {
                    WebPushEndpoint.push(CHANNELSIM, person1[i]);
                    CurLocationInfo ci1 = new CurLocationInfo();
                    ci1.setPersonDbid(Long.parseLong(person1[i].split(",")[0]));
                    ci1.setX(((Long.parseLong(person1[i].split(",")[1]) - 190)/2) * heatRate);
                    ci1.setY((338 - (Long.parseLong(person1[i].split(",")[2]) - 75)/2) * heatRate);
                    ci1.setPosTime(new Date(new Date().getTime()));
                    simInfo.add(ci1);
                    try {
                        sleep(50);
                    } catch (Exception e) {
                        log.error(e.getMessage());
                    }
                    WebPushEndpoint.push(CHANNELSIM, person2[i]);
                    CurLocationInfo ci2 = new CurLocationInfo();
                    ci2.setPersonDbid(Long.parseLong(person2[i].split(",")[0]));
                    ci2.setX(((Long.parseLong(person2[i].split(",")[1]) - 190)/2) * heatRate);
                    ci2.setY((338 - (Long.parseLong(person2[i].split(",")[2]) - 60)/2) * heatRate);
                    ci2.setPosTime(new Date(new Date().getTime()));
                    simInfo.add(ci2);
                    try {
                        sleep(50);
                    } catch (Exception e) {
                        log.error(e.getMessage());
                    }
                    WebPushEndpoint.push(CHANNELSIM, person3[i]);
                    CurLocationInfo ci3 = new CurLocationInfo();
                    ci3.setPersonDbid(Long.parseLong(person3[i].split(",")[0]));
                    ci3.setX(((Long.parseLong(person3[i].split(",")[1]) - 890)/2) * heatRate);
                    ci3.setY((338 - (Long.parseLong(person3[i].split(",")[2]) - 52)/2) * heatRate);
                    ci3.setPosTime(new Date(new Date().getTime()));
                    simInfo.add(ci3);
                    try {
                        sleep(50);
                    } catch (Exception e) {
                        log.error(e.getMessage());
                    }
                    WebPushEndpoint.push(CHANNELSIM, person4[i]);
                    CurLocationInfo ci4 = new CurLocationInfo();
                    ci4.setPersonDbid(Long.parseLong(person4[i].split(",")[0]));
                    ci4.setX(((Long.parseLong(person4[i].split(",")[1]) - 270)/2) * heatRate);
                    ci4.setY((338 - (Long.parseLong(person4[i].split(",")[2]) - 100)/2) * heatRate);
                    ci4.setPosTime(new Date(new Date().getTime()));
                    simInfo.add(ci4);
                    try {
                        sleep(50);
                    } catch (Exception e) {
                        log.error(e.getMessage());
                    }
                    if (++i >= 800)
                        i = 0;
                }
                if(!connected) {
                    simInfo.clear();
                }
            }
        }.start();
    }

    public void trailClick() {
        if(!trail) {
            trail = true;
        }
        else if(trail) {
            trail = false;
            List<LocationPos> l1 = timeMaps.get("9959");
            if(l1 != null) {
                l1.clear();
            }
            List<LocationPos> l2 = timeMaps.get("10527");
            if(l2 != null) {
                l2.clear();
            }
            List<LocationPos> l3 = timeMaps.get("10547");
            if(l3 != null) {
                l3.clear();
            }
            List<LocationPos> l4 = timeMaps.get("10557");
            if(l4 != null) {
                l4.clear();
            }
            List<LocationPos> l5 = timeMaps.get("10559");
            if(l5 != null) {
                l5.clear();
            }
            List<LocationPos> l6 = timeMaps.get("10808");
            if(l6 != null) {
                l6.clear();
            }
            List<LocationPos> l7 = timeMaps.get("11227");
            if(l7 != null) {
                l7.clear();
            }
        }
    }

    public CartesianChartModel heatmapChartModel;

    public CartesianChartModel createHeatmapChartModel() {

        ChartSeries series = new ChartSeries();

        List<CurLocationInfo> curLocationInfos = this.getInfo();
        for (CurLocationInfo info : curLocationInfos) {
            Date now = new Date();
            //时间差：秒
            long sec = (now.getTime() - info.getPosTime().getTime()) / 1000;
            if (sec <= 1) { //时间间隔小于1秒
                series.setArray(info.getX(), info.getY(), 1);
            } else {
                series.setArray(info.getX(), info.getY(), 0);
            }
        }
        CartesianChartModel model = new CartesianChartModel();
        model.addSeries(series);

        return model;
    }

    public CartesianChartModel getHeatmapChartModel() {
        return heatmapChartModel;
    }

    private volatile List<CurLocationInfo> simInfo = new ArrayList<>();
    private volatile int mark = 0;
    public List<CurLocationInfo> getInfo() {

//        if(mark == 0) {
//            Long personDbids[] = {111L, 222L, 333L};
//            for (int i = 0; i < 20; ++i) {
//                Random ra1 = new Random();
//                CurLocationInfo ci = new CurLocationInfo();
//                int index = ra1.nextInt(3);
//                ci.setPersonDbid(personDbids[index]);
//                Random ra2 = new Random();
//                ci.setX(new Long(ra2.nextInt(900) + 1));
//                Random ra3 = new Random();
//                ci.setY(new Long(ra3.nextInt(400) + 1));
//                ci.setPosTime(new Date(new Date().getTime() + i * 10));
//                simInfo.add(ci);
//            }
//        }

//        if(mark == 0) {
//            mark = 1;
//            new Thread() {
//                public void run() {
//                    Long personDbids[] = {111L, 222L, 333L};
//                    for (int i = 0; i < 5000; ++i) {
//                        Random ra1 = new Random();
//                        CurLocationInfo ci = new CurLocationInfo();
//                        int index = ra1.nextInt(3);
//                        ci.setPersonDbid(personDbids[index]);
//                        Random ra2 = new Random();
//                        ci.setX(new Long(ra2.nextInt(900) + 1));
//                        Random ra3 = new Random();
//                        ci.setY(new Long(ra3.nextInt(400) + 1));
//                        ci.setPosTime(new Date(new Date().getTime() + i * 10));
//                        simInfo.add(ci);
//                        if(i == 5000)
//                            mark = 0;
//                        try {
//                            sleep(200);
//                        }
//                        catch (InterruptedException e) {
//                            log.error(e.getMessage());
//                        }
//                    }
//                }
//            }.start();
//        }
        return simInfo;
    }

    public List<String> getPersonList() {
        return personList;
    }

    public void setPersonList(List<String> personList) {
        this.personList = personList;
    }

    public List<String> getToPersonList() {
        return toPersonList;
    }

    public void setToPersonList(List<String> toPersonList) {
        this.toPersonList = toPersonList;
    }

    public float getAgv1Distance() {
        return agv1Distance;
    }

    public void setAgv1Distance(float agv1Distance) {
        this.agv1Distance = agv1Distance;
    }

    public float getAgv2Distance() {
        return agv2Distance;
    }

    public void setAgv2Distance(float agv2Distance) {
        this.agv2Distance = agv2Distance;
    }

    public int getAgv1Id() {
        return agv1Id;
    }

    public void setAgv1Id(int agv1Id) {
        this.agv1Id = agv1Id;
    }

    public int getAgv1LastX() {
        return agv1LastX;
    }

    public void setAgv1LastX(int agv1LastX) {
        this.agv1LastX = agv1LastX;
    }

    public int getAgv1LastY() {
        return agv1LastY;
    }

    public void setAgv1LastY(int agv1LastY) {
        this.agv1LastY = agv1LastY;
    }

    public int getAgv2Id() {
        return agv2Id;
    }

    public void setAgv2Id(int agv2Id) {
        this.agv2Id = agv2Id;
    }

    public int getAgv2LastX() {
        return agv2LastX;
    }

    public void setAgv2LastX(int agv2LastX) {
        this.agv2LastX = agv2LastX;
    }

    public int getAgv2LastY() {
        return agv2LastY;
    }

    public void setAgv2LastY(int agv2LastY) {
        this.agv2LastY = agv2LastY;
    }

    private boolean heatmapEnabled;

    public boolean isHeatmapEnabled() {
        return heatmapEnabled;
    }

    public void setHeatmapEnabled(boolean heatmapEnabled) {
        this.heatmapEnabled = heatmapEnabled;
    }
}