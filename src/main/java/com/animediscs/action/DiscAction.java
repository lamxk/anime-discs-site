package com.animediscs.action;

import com.animediscs.dao.Dao;
import com.animediscs.model.*;
import com.animediscs.support.BaseAction;
import org.apache.commons.lang3.time.DateUtils;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

import static com.animediscs.util.Parser.parseDate;

public class DiscAction extends BaseAction {

    private Dao dao;

    private Long id;
    private String name;
    private String title;
    private String sname;
    private String type;
    private boolean amzver;
    private String release;
    private String filter;

    @Autowired
    public void setDao(Dao dao) {
        this.dao = dao;
    }

    public void view() throws Exception {
        Disc disc = dao.get(Disc.class, id);
        JSONObject object = new JSONObject();
        object.put("id", disc.getId());
        object.put("asin", disc.getAsin());
        object.put("title", disc.getTitle());
        object.put("japan", disc.getJapan());
        object.put("sname", disc.getSname());
        object.put("type", disc.getType().ordinal());
        object.put("amzver", disc.isAmzver());
        if (disc.getRelease() != null) {
            object.put("release", disc.getRelease().getTime());
        }
        DiscRank rank = disc.getRank();
        if (rank != null) {
            if (rank.getPadt1() != null) {
                object.put("rank1", rank.getPark1());
                object.put("date1", rank.getPadt1().getTime());
            }
            if (rank.getPadt2() != null) {
                object.put("rank2", rank.getPark2());
                object.put("date2", rank.getPadt2().getTime());
            }
            if (rank.getPadt3() != null) {
                object.put("rank3", rank.getPark3());
                object.put("date3", rank.getPadt3().getTime());
            }
            if (rank.getPadt4() != null) {
                object.put("rank4", rank.getPark4());
                object.put("date4", rank.getPadt4().getTime());
            }
            if (rank.getPadt5() != null) {
                object.put("rank5", rank.getPark5());
                object.put("date5", rank.getPadt5().getTime());
            }
        }
        DiscSakura sakura = disc.getSakura();
        if (sakura != null) {
            object.put("curk", sakura.getCurk());
            object.put("prrk", sakura.getPrrk());
            object.put("cupt", sakura.getCupt());
            object.put("cubk", sakura.getCubk());
            object.put("sday", sakura.getSday());
        } else if (disc.getRelease() != null) {
            object.put("sday", getSday(disc));
        }
        responseJson(object.toString());
    }

    private int getSday(Disc disc) {
        long currentTime = System.currentTimeMillis();
        long releaseTime = disc.getRelease().getTime() - 3600000L;
        return (int) ((releaseTime - currentTime) / 86400000L);
    }

    public void list() throws Exception {
        DiscList discList;
        if ("table".equals(filter)) {
            discList = getDiscByTable();
        } else if ("type".equals(filter)) {
            if ("cd".equals(type)) {
                discList = getDiscsOfCD();
            } else {
                discList = getDiscsOfDVD();
            }
        } else {
            discList = getAllDiscs();
        }
        JSONObject object = new JSONObject();
        if (discList.getId() != null) {
            object.put("id", discList.getId());
        }
        object.put("name", discList.getName());
        object.put("title", discList.getTitle());
        object.put("discs", buildDiscsForList(discList));
        responseJson(object.toString());
    }

    private DiscList getAllDiscs() {
        DiscList discList = new DiscList();
        discList.setName("all_disc");
        discList.setTitle("全部碟片");
        List<Disc> discs = dao.findAll(Disc.class);
        Collections.sort(discs, Disc.sortByAmazon());
        discList.setDiscs(discs);
        return discList;
    }

    private DiscList getDiscByTable() throws IOException {
        DiscList discList = dao.lookup(DiscList.class, "name", name);
        if (discList != null) {
            dao.execute(session -> {
                List<Disc> discs = dao.get(DiscList.class, discList.getId()).getDiscs();
                Collections.sort(discs, Disc.sortByAmazon());
                discList.setDiscs(discs);
            });
        } else {
            responseError("未找到指定的动画列表");
        }
        return discList;
    }

    private DiscList getDiscsOfCD() {
        DiscList discList = new DiscList();
        discList.setName("all_cd");
        discList.setTitle("所有音乐碟片");
        dao.execute(session -> {
            List<Disc> discs = session.createCriteria(Disc.class)
                    .add(Restrictions.eq("type", DiscType.CD))
                    .list();
            Collections.sort(discs, Disc.sortByAmazon());
            discs.forEach(disc -> {
                DiscSakura sakura = disc.getSakura();
                if (sakura == null) {
                    sakura = new DiscSakura();
                }
                sakura.setDisc(disc);
                sakura.setCurk(disc.getRank().getPark1());
                sakura.setPrrk(disc.getRank().getPark2());
                sakura.setSday(getSday(disc));
                if (needUpdateCupt(sakura)) {
                    sakura.setCupt(getCupt(disc));
                }
                sakura.setDate(new Date());
                dao.save(sakura);
                disc.setSakura(sakura);
            });
            discList.setDiscs(discs);
        });
        return discList;
    }

    private boolean needUpdateCupt(DiscSakura sakura) {
        if (sakura.getDate() == null) {
            return true;
        }
        if (sakura.getSday() < 0) {
            return false;
        }
        Date date = new Date();
        date = DateUtils.setMinutes(date, 0);
        date = DateUtils.setSeconds(date, 0);
        date = DateUtils.setMilliseconds(date, 0);
        return date.compareTo(sakura.getDate()) > 0;
    }

    private int getCupt(Disc disc) {
        return (int) (0.5 + dao.query(session -> {
            List<DiscRecord> list = session.createCriteria(DiscRecord.class)
                    .add(Restrictions.eq("disc", disc))
                    .addOrder(Order.desc("date"))
                    .list();
            Date date = new Date();
            date = DateUtils.addHours(date, -1);
            date = DateUtils.setMinutes(date, 0);
            date = DateUtils.setSeconds(date, 0);
            date = DateUtils.setMilliseconds(date, 0);

            List<DiscRecord> dest = new ArrayList<>(list.size() * 2);
            while (list.size() > 0) {
                Date release = DateUtils.addHours(disc.getRelease(), -1);
                DiscRecord discRecord = list.remove(0);
                while (date.compareTo(discRecord.getDate()) >= 0) {
                    if (date.compareTo(release) < 0) {
                        DiscRecord record = new DiscRecord();
                        int rank = discRecord.getRank();
                        record.setRank(rank);
                        record.setDate(date);
                        record.setAdpt(150 / Math.exp(Math.log(rank) / Math.log(5.25)));
                        dest.add(record);
                    }
                    date = DateUtils.addHours(date, -1);
                }
            }

            double cupt = 0;
            for (int i = dest.size() - 1; i >= 0; i--) {
                cupt += dest.get(i).getAdpt();
            }
            return cupt;
        }));
    }

    private DiscList getDiscsOfDVD() {
        DiscList discList = new DiscList();
        discList.setName("all_cd");
        discList.setTitle("所有动画碟片");
        dao.execute(session -> {
            List<Disc> discs = session.createCriteria(Disc.class)
                    .add(Restrictions.ne("type", DiscType.CD))
                    .list();
            Collections.sort(discs, Disc.sortByAmazon());
            discList.setDiscs(discs);
        });
        return discList;
    }

    private JSONArray buildDiscsForList(DiscList discList) {
        JSONArray array = new JSONArray();
        discList.getDiscs().forEach(disc -> {
            JSONObject object = new JSONObject();
            object.put("id", disc.getId());
            object.put("asin", disc.getAsin());
            object.put("title", disc.getTitle());
            object.put("type", disc.getType().ordinal());
            object.put("amzver", disc.isAmzver());
            if (disc.getSname() == null || disc.getSname().isEmpty()) {
                object.put("sname", disc.getTitle());
            } else {
                object.put("sname", disc.getSname());
            }
            if (disc.getRelease() != null) {
                object.put("release", disc.getRelease().getTime());
            }
            DiscRank rank = disc.getRank();
            if (rank != null) {
                if (discList.isTop100()) {
                    if (rank.getSpdt() != null) {
                        object.put("arnk", rank.getSpdt());
                        object.put("amdt", rank.getSpdt().getTime());
                    }
                } else {
                    if (rank.getPadt() != null) {
                        object.put("arnk", rank.getPark());
                        object.put("amdt", rank.getPadt().getTime());
                    }
                }
                object.put("rank1", rank.getPark1());
                object.put("rank2", rank.getPark2());
                object.put("rank3", rank.getPark3());
                object.put("rank4", rank.getPark4());
                object.put("rank5", rank.getPark5());
            }
            DiscSakura sakura = disc.getSakura();
            if (sakura != null) {
                object.put("curk", sakura.getCurk());
                object.put("prrk", sakura.getPrrk());
                object.put("cupt", sakura.getCupt());
                object.put("cubk", sakura.getCubk());
                object.put("sday", sakura.getSday());
            } else if (disc.getRelease() != null) {
                object.put("sday", getSday(disc));
            }
            array.put(object);
        });
        return array;
    }

    public void edit() throws Exception {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
        Date release = parseDate(dateFormat, this.release);

        Disc disc = dao.get(Disc.class, id);
        disc.setTitle(title);
        disc.setSname(sname);
        disc.setType(DiscType.valueOf(type));
        disc.setAmzver(amzver);
        disc.setRelease(release);
        dao.update(disc);
        responseSuccess();
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setSname(String sname) {
        this.sname = sname;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setAmzver(boolean amzver) {
        this.amzver = amzver;
    }

    public void setRelease(String release) {
        this.release = release;
    }

    public void setFilter(String filter) {
        this.filter = filter;
    }

}
