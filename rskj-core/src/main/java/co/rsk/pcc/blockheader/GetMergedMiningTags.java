/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.pcc.blockheader;

import co.rsk.config.RskMiningConstants;
import co.rsk.pcc.ExecutionEnvironment;
import co.rsk.pcc.NativeMethod;
import org.apache.commons.lang3.ArrayUtils;
import org.ethereum.core.Block;
import org.ethereum.core.CallTransaction;
import org.ethereum.util.ByteUtil;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * This implements the "getMergedMiningTags" method
 * that belongs to the BlockHeaderContract native contract.
 *
 * @author Diego Masini
 */
public class GetMergedMiningTags extends NativeMethod {
    private final BlockAccessor blockAccessor;

    private final CallTransaction.Function function = CallTransaction.Function.fromSignature(
            "getMergedMiningTags",
            new String[]{"uint256"},
            new String[]{"bytes"}
    );

    public GetMergedMiningTags(ExecutionEnvironment executionEnvironment, BlockAccessor blockAccessor) {
        super(executionEnvironment);
        this.blockAccessor = blockAccessor;
    }

    @Override
    public CallTransaction.Function getFunction() {
        return function;
    }

    @Override
    public Object execute(Object[] arguments) {
        short blockDepth = ((BigInteger) arguments[0]).shortValueExact();

        Block block = blockAccessor.getBlock(blockDepth, getExecutionEnvironment());
        if (block == null) {
            return ByteUtil.EMPTY_BYTE_ARRAY;
        }

        byte[] mergedMiningTx = block.getBitcoinMergedMiningCoinbaseTransaction();

        List<Byte> mergedMiningTxAsList = Arrays.asList(ArrayUtils.toObject(mergedMiningTx));
        List<Byte> rskTagBytesAsList = Arrays.asList(ArrayUtils.toObject(RskMiningConstants.RSK_TAG));

        int rskTagPosition = Collections.lastIndexOfSubList(mergedMiningTxAsList, rskTagBytesAsList);

        // if RSK Tag not found, return an empty byte array
        if (rskTagPosition == -1) {
            return ByteUtil.EMPTY_BYTE_ARRAY;
        }

        int additionalTagsStartIndex = rskTagPosition + RskMiningConstants.RSK_TAG.length + RskMiningConstants.BLOCK_HEADER_HASH_SIZE;
        return Arrays.copyOfRange(mergedMiningTx, additionalTagsStartIndex, mergedMiningTx.length);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean onlyAllowsLocalCalls() {
        return false;
    }

    @Override
    public long getGas(Object[] parsedArguments, byte[] originalData) {
        return 1000L + super.getGas(parsedArguments, originalData);
    }
}