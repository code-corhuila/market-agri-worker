package co.edu.corhuila.marketagri.worker.domain.model;

/**
 * The texts the user reads (ADR-016 point 3; api-contract §6): Spanish with <i>usted</i>, the
 * product, the quantity, the amount and the link to the transaction, and no other identifier.
 */
public final class Notices {

    private final String publicBaseUrl;

    /** {@code publicBaseUrl}: the address of the interface, the same as the {@code redirect-url} of Wompi. */
    public Notices(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
    }

    public Notice purchaseConfirmed(TransactionConfirmed event, Contact buyer) {
        return new Notice(buyer.email(), "Su compra fue confirmada", """
                Hola, %s:

                Su pago fue confirmado. Estos son los datos de su compra:

                %s
                Puede ver el detalle en %s

                Marketplace Agrícola Huila
                """.formatted(buyer.name(), details(event), link(event)));
    }

    public Notice saleConfirmed(TransactionConfirmed event, Contact producer) {
        return new Notice(producer.email(), "Tiene una venta confirmada", """
                Hola, %s:

                Se confirmó el pago de una venta suya. Estos son los datos:

                %s
                Puede ver el detalle en %s

                Marketplace Agrícola Huila
                """.formatted(producer.name(), details(event), link(event)));
    }

    private static String details(TransactionConfirmed event) {
        return "Producto: " + event.productName() + "\n"
                + "Cantidad: " + Formats.quantity(event.quantity(), event.unit()) + "\n"
                + "Total: " + Formats.cents(event.amountCents()) + "\n";
    }

    private String link(TransactionConfirmed event) {
        return publicBaseUrl + "/transacciones/" + event.transactionId();
    }
}
