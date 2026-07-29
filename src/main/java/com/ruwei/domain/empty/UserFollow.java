package com.ruwei.domain.empty;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 关注关系表
 * @TableName user_follow
 */
@TableName(value ="user_follow")
@Data
public class UserFollow {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 主动关注者
     */
    private Long followerid;

    /**
     * 被关注者
     */
    private Long followeeid;

    /**
     * 1关注 2已取消关注
     */
    private Integer status;

    /**
     * 
     */
    private Date createdat;

    /**
     * 
     */
    private Date updatedat;

    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        }
        if (that == null) {
            return false;
        }
        if (getClass() != that.getClass()) {
            return false;
        }
        UserFollow other = (UserFollow) that;
        return (this.getId() == null ? other.getId() == null : this.getId().equals(other.getId()))
            && (this.getFollowerid() == null ? other.getFollowerid() == null : this.getFollowerid().equals(other.getFollowerid()))
            && (this.getFolloweeid() == null ? other.getFolloweeid() == null : this.getFolloweeid().equals(other.getFolloweeid()))
            && (this.getStatus() == null ? other.getStatus() == null : this.getStatus().equals(other.getStatus()))
            && (this.getCreatedat() == null ? other.getCreatedat() == null : this.getCreatedat().equals(other.getCreatedat()))
            && (this.getUpdatedat() == null ? other.getUpdatedat() == null : this.getUpdatedat().equals(other.getUpdatedat()));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getId() == null) ? 0 : getId().hashCode());
        result = prime * result + ((getFollowerid() == null) ? 0 : getFollowerid().hashCode());
        result = prime * result + ((getFolloweeid() == null) ? 0 : getFolloweeid().hashCode());
        result = prime * result + ((getStatus() == null) ? 0 : getStatus().hashCode());
        result = prime * result + ((getCreatedat() == null) ? 0 : getCreatedat().hashCode());
        result = prime * result + ((getUpdatedat() == null) ? 0 : getUpdatedat().hashCode());
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append(" [");
        sb.append("Hash = ").append(hashCode());
        sb.append(", id=").append(id);
        sb.append(", followerid=").append(followerid);
        sb.append(", followeeid=").append(followeeid);
        sb.append(", status=").append(status);
        sb.append(", createdat=").append(createdat);
        sb.append(", updatedat=").append(updatedat);
        sb.append("]");
        return sb.toString();
    }
}