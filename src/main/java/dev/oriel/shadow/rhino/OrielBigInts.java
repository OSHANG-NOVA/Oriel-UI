/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package dev.oriel.shadow.rhino;

import java.math.BigInteger;

/**
 * Size checks for BigInt arithmetic. A single BigInteger operation runs in Java, out of sight of
 * the instruction observer, and its cost grows faster than its operands: squaring a value 30 times,
 * or {@code 1n << 2n ** 30n}, would take minutes or exhaust the heap. Every BigInt multiplication,
 * power, shift and parse from text goes through here first, and results larger than {@link
 * #maxBits} throw a RangeError.
 */
public final class OrielBigInts {
    /** The largest BigInt, in bits; process-wide, and unlimited unless an embedding sets it. */
    private static volatile long maxBits = Long.MAX_VALUE;

    private OrielBigInts() {}

    public static void setMaxBits(long bits) {
        maxBits = bits;
    }

    public static long maxBits() {
        return maxBits;
    }

    private static void check(long bits) {
        if (bits > maxBits)
            throw ScriptRuntime.rangeError("BigInt larger than " + maxBits + " bits");
    }

    public static BigInteger multiply(BigInteger a, BigInteger b) {
        check((long) a.bitLength() + b.bitLength());
        return a.multiply(b);
    }

    public static BigInteger pow(BigInteger a, int exponent) {
        if (a.abs().compareTo(BigInteger.ONE) > 0) check((long) (a.bitLength() - 1) * exponent + 1);
        return a.pow(exponent);
    }

    public static BigInteger shiftLeft(BigInteger a, int n) {
        if (n > 0 && a.signum() != 0) check((long) a.bitLength() + n);
        return a.shiftLeft(n);
    }

    public static BigInteger shiftRight(BigInteger a, int n) {
        if (n < 0 && a.signum() != 0) check((long) a.bitLength() - (long) n);
        return a.shiftRight(n);
    }

    /** Parses {@code text} in {@code radix}; parsing is quadratic in the number of digits. */
    public static BigInteger parse(String text, int radix) {
        check((long) Math.ceil(text.length() * (Math.log(Math.max(2, radix)) / Math.log(2))));
        return new BigInteger(text, radix);
    }

    public static BigInteger parse(String text) {
        return parse(text, 10);
    }
}
