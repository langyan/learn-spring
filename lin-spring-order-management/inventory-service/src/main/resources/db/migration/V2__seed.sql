-- 演示种子数据（商品目录 + 库存）。product 410 库存为 1，用于并发防超卖演示。
INSERT INTO products (product_id, name, description, unit_price, currency) VALUES
    (101, 'Mechanical Keyboard', 'Hot-swappable switches, RGB backlit', 79.90, 'USD'),
    (205, '4K Monitor 27"', 'IPS panel, 144Hz, USB-C 90W', 349.00, 'USD'),
    (301, 'USB-C Dock', '12-in-1 docking station', 129.50, 'USD'),
    (410, 'Laptop Pro 14', 'Last unit — oversell demo product', 1299.00, 'USD'),
    (520, 'Noise-Cancel Headphones', 'ANC over-ear, 40h battery', 199.00, 'USD');

INSERT INTO inventory (product_id, available_quantity, reserved_quantity) VALUES
    (101, 100, 0),
    (205, 50, 0),
    (301, 200, 0),
    (410, 1, 0),
    (520, 30, 0);
