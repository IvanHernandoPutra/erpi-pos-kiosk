/** @odoo-module **/

import { PaymentScreen } from "@point_of_sale/app/screens/payment_screen/payment_screen";
import { patch } from "@web/core/utils/patch";

patch(PaymentScreen.prototype, {
    /**
     * Set nominal cepat untuk pembayaran kasir touch-friendly
     * @param {string|number} typeOrAmount - 'exact', 50000, 100000, 200000, atau nominal lain
     */
    setQuickCashAmount(typeOrAmount) {
        const currentOrder = this.currentOrder;
        if (!currentOrder) return;

        let targetAmount = 0;
        const totalDue = currentOrder.get_due();
        const totalWithTax = currentOrder.get_total_with_tax();

        if (typeOrAmount === "exact") {
            targetAmount = totalDue > 0 ? totalDue : totalWithTax;
        } else {
            targetAmount = parseFloat(typeOrAmount);
        }

        if (isNaN(targetAmount) || targetAmount <= 0) return;

        // Cari atau tambahkan payment line metode tunai (cash)
        let cashMethod = this.pos.payment_methods.find((pm) => pm.is_cash_count);
        if (!cashMethod && this.pos.payment_methods.length > 0) {
            cashMethod = this.pos.payment_methods[0];
        }

        if (cashMethod) {
            let cashLine = currentOrder.get_paymentlines().find(
                (pl) => pl.payment_method.id === cashMethod.id
            );
            if (!cashLine) {
                currentOrder.add_paymentline(cashMethod);
                cashLine = currentOrder.selected_paymentline;
            }
            if (cashLine) {
                cashLine.set_amount(targetAmount);
            }
        }
    },

    /**
     * Pilih metode pembayaran cepat QRIS Dinamis
     */
    selectQrisPaymentMethod() {
        const currentOrder = this.currentOrder;
        if (!currentOrder) return;

        // Cari payment method dengan kata 'qris' atau non-cash
        const qrisMethod = this.pos.payment_methods.find(
            (pm) => pm.name.toLowerCase().includes("qris") || pm.name.toLowerCase().includes("bank")
        );

        if (qrisMethod) {
            this.addNewPaymentLine(qrisMethod);
        } else if (this.pos.payment_methods.length > 1) {
            this.addNewPaymentLine(this.pos.payment_methods[1]);
        }
    },
});
