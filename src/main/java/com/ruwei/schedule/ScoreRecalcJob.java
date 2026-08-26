package com.ruwei.schedule;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruwei.common.ThrowUtils;
import com.ruwei.domain.Enum.PostAuditStatusEnum;
import com.ruwei.domain.Enum.PostVisibilityEnum;
import com.ruwei.domain.empty.Post;
import com.ruwei.service.PostService;
import jakarta.annotation.Resource;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
//热度对账，每五分钟执行一次
public class ScoreRecalcJob {

    @Value("${forum-score-config.likeW}")
    private double likeW;
    @Value("${forum-score-config.commentW}")
    private double commentW;
    @Value("${forum-score-config.forum.score.commentW}")
    private double collectW;
    @Value("${forum-score-config.forum.score.shareW}")
    private double shareW;
    private double tauHours;

    @Resource
    private PostService postService;

    public void recalc(){
        //1.分页去全量【已发布】，并且是已公开
        long current=1;
        long pageSize=500;

        while(true){
            Page<Post> page = postService.lambdaQuery().eq(Post::getAuditStatus, PostAuditStatusEnum.APPROVED.getCode())
                    .eq(Post::getVisibility, PostVisibilityEnum.PUBLIC.getCode())
                    .orderByAsc(Post::getId)
                    .page(new Page<>(current++, pageSize));
            List<Post> pageRecords = page.getRecords();
            if(pageRecords.isEmpty()){
                break;
            }
            //需要进行逐条计算，并更新（收集变化量》0.001的才更新）
            List<Post> toUpdate = pageRecords.stream()
                    //过滤没有创建的时间的（一定会有创建时间）
                    .filter(p -> p.getCreatedAt() != null)
                    .map(p -> {
                        p.setScore(calsScore(p));
                        return p;
                    })
                    //进行剔除为零的还有变化量十分小的
                    .filter(p -> p.getScore() != null && p.getScore().compareTo(BigDecimal.ZERO) > 0)
                    .toList();
            if(!toUpdate.isEmpty()) postService.updateBatchById(toUpdate);
            //如果数量不足那就直接退出后续肯定也不足够
            if (page.getRecords().size() < pageSize) break;
        }
    }

    private BigDecimal calsScore(Post p){
        //1.计算帖子的发布时长
        long dtHours = (System.currentTimeMillis() - p.getCreatedAt().getTime()) / 3600_000L;
        double base = likeW * Math.log10((p.getLikeCount()) + 1.0)
                + commentW * Math.log10((p.getCommentCount()) + 1.0)
                + collectW * Math.log10((p.getCollectCount()) + 1.0)
                + shareW * Math.log10((p.getShareCount()) + 1.0);

        //采用
        double score = base * Math.exp(-dtHours / (double) tauHours);
        //转化为BigDecimal存储
        return BigDecimal.valueOf(score).setScale(4, RoundingMode.HALF_UP);
    }
}
