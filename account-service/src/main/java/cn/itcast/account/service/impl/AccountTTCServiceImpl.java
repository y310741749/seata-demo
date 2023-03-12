package cn.itcast.account.service.impl;

import cn.itcast.account.entity.AccountFreeze;
import cn.itcast.account.mapper.AccountFreezeMapper;
import cn.itcast.account.mapper.AccountMapper;
import cn.itcast.account.service.AccountTTCService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.seata.core.context.RootContext;
import io.seata.rm.tcc.api.BusinessActionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class AccountTTCServiceImpl implements AccountTTCService {

    @Autowired
    private AccountMapper accountMapper;

    @Autowired
    private AccountFreezeMapper accountFreezeMapper;

    @Override
    @Transactional
    public void deduct(String userId, int money) {
        //业务悬挂判断
        AccountFreeze accountFreeze1 = accountFreezeMapper.selectById(RootContext.getXID());
        if (null != accountFreeze1) {
            return;
        }
        accountMapper.deduct(userId, money);

        AccountFreeze accountFreeze = AccountFreeze.builder()
                .userId(userId)
                .freezeMoney(money)
                .state(AccountFreeze.State.TRY)
                .xid(RootContext.getXID()).build();
        accountFreezeMapper.insert(accountFreeze);
    }

    @Override
    public boolean confirm(BusinessActionContext ctx) {
        String xid = ctx.getXid();
        return accountFreezeMapper.deleteById(xid) > 0;
    }

    @Override
    public boolean cancel(BusinessActionContext ctx) {
        AccountFreeze accountFreeze = accountFreezeMapper.selectById(ctx.getXid());
        //空回滚判断,判断freezz是否为null，为null表示try还未执行
        if (null == accountFreeze) {
            AccountFreeze accountFreezeNull = AccountFreeze.builder()
                    .userId(ctx.getActionContext("userId").toString())
                    .freezeMoney(Integer.parseInt(ctx.getActionContext("money").toString()))
                    .state(AccountFreeze.State.CANCEL)
                    .xid(RootContext.getXID()).build();
            accountFreezeMapper.insert(accountFreezeNull);
            return true;
        }
        //幂等性判断
        if (accountFreeze.getState() == AccountFreeze.State.CANCEL) {
            return true;
        }
        accountMapper.refund(accountFreeze.getUserId(), accountFreeze.getFreezeMoney());
        accountFreeze.setFreezeMoney(0);
        accountFreeze.setState(AccountFreeze.State.CANCEL);
        return accountFreezeMapper.updateById(accountFreeze) == 1;
    }
}
